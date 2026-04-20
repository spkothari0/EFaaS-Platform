package com.efaas.lending;

import com.efaas.common.dto.FinancialProfile;
import com.efaas.lending.client.FinancialProfileClient;
import com.efaas.lending.entity.LoanStatus;
import com.efaas.lending.repository.LoanRepository;
import com.efaas.lending.repository.RepaymentInstallmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {"lending-topic", "payments-topic"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class LoanApplicationIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("lending_db")
            .withUsername("efaas")
            .withPassword("dev_password");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired LoanRepository loanRepository;
    @Autowired RepaymentInstallmentRepository installmentRepository;

    @MockitoBean
    FinancialProfileClient financialProfileClient;

    private UUID tenantId;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        installmentRepository.deleteAll();
        loanRepository.deleteAll();
    }

    @Test
    void applyForLoan_approvedProfile_returnsApproved() throws Exception {
        stubFinancialProfile(15000.0, 12000.0, 15000.0, 35);

        String body = """
            {
              "plaidAccountId": "%s",
              "requestedAmountCents": 120000,
              "termMonths": 12,
              "purpose": "Home improvement",
              "applicantUserId": "%s"
            }
            """.formatted(accountId, UUID.randomUUID());

        mockMvc.perform(post("/api/v1/loans/apply")
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.creditScore").isNumber())
                .andExpect(jsonPath("$.monthlyPaymentCents").isNumber());

        assertThat(loanRepository.findByTenantId(tenantId)).hasSize(1);
        var loan = loanRepository.findByTenantId(tenantId).get(0);
        assertThat(installmentRepository.findByLoanOrderByInstallmentNumberAsc(loan)).hasSize(12);
    }

    @Test
    void applyForLoan_weakProfile_returnsDenied() throws Exception {
        stubFinancialProfile(100.0, 20.0, 200.0, 1);

        String body = """
            {
              "plaidAccountId": "%s",
              "requestedAmountCents": 1000000,
              "termMonths": 6,
              "purpose": "Business",
              "applicantUserId": "%s"
            }
            """.formatted(accountId, UUID.randomUUID());

        mockMvc.perform(post("/api/v1/loans/apply")
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DENIED"))
                .andExpect(jsonPath("$.monthlyPaymentCents").doesNotExist());
    }

    @Test
    void getLoan_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/loans/{loanId}", UUID.randomUUID())
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isNotFound());
    }

    @Test
    void disburseLoan_approvedLoan_setsDisbursed() throws Exception {
        stubFinancialProfile(15000.0, 12000.0, 15000.0, 35);
        UUID applicantId = UUID.randomUUID();

        String body = """
            {
              "plaidAccountId": "%s",
              "requestedAmountCents": 120000,
              "termMonths": 12,
              "purpose": "Education",
              "applicantUserId": "%s"
            }
            """.formatted(accountId, applicantId);

        String applyResult = mockMvc.perform(post("/api/v1/loans/apply")
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID loanId = objectMapper.readTree(applyResult).get("loanId").textValue() != null
                ? UUID.fromString(objectMapper.readTree(applyResult).get("loanId").textValue())
                : null;
        assertThat(loanId).isNotNull();

        mockMvc.perform(post("/api/v1/loans/{loanId}/disburse", loanId)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISBURSED"));

        var loan = loanRepository.findById(loanId).orElseThrow();
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.DISBURSED);
        assertThat(loan.getDisbursedAt()).isNotNull();
    }

    @Test
    void getRepaymentSchedule_approvedLoan_returnsInstallments() throws Exception {
        stubFinancialProfile(15000.0, 12000.0, 15000.0, 35);
        UUID applicantId = UUID.randomUUID();

        String body = """
            {
              "plaidAccountId": "%s",
              "requestedAmountCents": 120000,
              "termMonths": 6,
              "purpose": "Vehicle",
              "applicantUserId": "%s"
            }
            """.formatted(accountId, applicantId);

        String applyResult = mockMvc.perform(post("/api/v1/loans/apply")
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getContentAsString();

        UUID loanId = UUID.fromString(objectMapper.readTree(applyResult).get("loanId").textValue());

        mockMvc.perform(get("/api/v1/loans/{loanId}/schedule", loanId)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installments").isArray())
                .andExpect(jsonPath("$.installments.length()").value(6))
                .andExpect(jsonPath("$.totalAmountCents").isNumber());
    }

    private void stubFinancialProfile(double current, double available, double income, int txCount) {
        FinancialProfile profile = new FinancialProfile(
                tenantId, accountId, current, available, null, income, txCount, LocalDate.now());
        when(financialProfileClient.getFinancialProfile(any(), any())).thenReturn(profile);
    }
}
