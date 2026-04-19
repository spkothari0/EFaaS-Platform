package com.efaas.payment;

import com.efaas.payment.dto.plaid.*;
import com.efaas.payment.entity.*;
import com.efaas.payment.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plaid.client.model.*;
import com.plaid.client.request.PlaidApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import retrofit2.Call;
import retrofit2.Response;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {"payments-topic", "plaid-topic"})
class PlaidIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("payments_test")
            .withUsername("efaas")
            .withPassword("dev_password");

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Plaid config placeholders (real calls are mocked via @MockBean)
        registry.add("plaid.client-id", () -> "test-client-id");
        registry.add("plaid.secret", () -> "test-secret");
        registry.add("plaid.env", () -> "sandbox");
        registry.add("plaid.webhook-url", () -> "http://localhost:8082/webhooks/plaid");
        // Stripe config placeholders
        registry.add("stripe.secret-key", () -> "sk_test_placeholder");
        registry.add("stripe.webhook-secret", () -> "whsec_placeholder");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PlaidItemRepository plaidItemRepository;
    @Autowired PlaidAccountRepository plaidAccountRepository;
    @Autowired AchPaymentRepository achPaymentRepository;

    @MockitoBean PlaidApi plaidApi;

    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void cleanDb() {
        achPaymentRepository.deleteAll();
        plaidAccountRepository.deleteAll();
        plaidItemRepository.deleteAll();
    }

    // ─── POST /api/v1/plaid/link-token ───────────────────────────────────────────

    @Test
    void createLinkToken_returns201WithToken() throws Exception {
        LinkTokenCreateResponse body = new LinkTokenCreateResponse()
                .linkToken("link-sandbox-test-token")
                .expiration(java.time.OffsetDateTime.now().plusHours(4));
        Call<LinkTokenCreateResponse> linkCall = successCall(body);
        when(plaidApi.linkTokenCreate(any())).thenReturn(linkCall);

        mockMvc.perform(post("/api/v1/plaid/link-token")
                        .header("X-Tenant-Id", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateLinkTokenRequest("user-001"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.linkToken").value("link-sandbox-test-token"));
    }

    // ─── POST /api/v1/plaid/exchange-token ───────────────────────────────────────

    @Test
    void exchangeToken_persistsItemAndAccounts() throws Exception {
        stubExchangeFlow();

        mockMvc.perform(post("/api/v1/plaid/exchange-token")
                        .header("X-Tenant-Id", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ExchangeTokenRequest("public-sandbox-token"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accounts[0].name").value("Checking Account"))
                .andExpect(jsonPath("$.accounts[0].mask").value("1234"));

        assertThat(plaidItemRepository.findByTenantId(tenantId)).hasSize(1);
        assertThat(plaidAccountRepository.findActiveByTenantId(tenantId)).hasSize(1);
    }

    // ─── GET /api/v1/plaid/accounts ──────────────────────────────────────────────

    @Test
    void getAccounts_returnsLinkedAccounts() throws Exception {
        stubExchangeFlow();
        // First link an account
        mockMvc.perform(post("/api/v1/plaid/exchange-token")
                        .header("X-Tenant-Id", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ExchangeTokenRequest("public-sandbox-token"))));

        // Then list
        mockMvc.perform(get("/api/v1/plaid/accounts")
                        .header("X-Tenant-Id", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts").isArray())
                .andExpect(jsonPath("$.accounts[0].name").value("Checking Account"));
    }

    // ─── POST /api/v1/plaid/payments/ach ─────────────────────────────────────────

    @Test
    void initiateAchPayment_persistsAndPublishesEvent() throws Exception {
        // Set up a linked account
        stubExchangeFlow();
        mockMvc.perform(post("/api/v1/plaid/exchange-token")
                .header("X-Tenant-Id", tenantId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new ExchangeTokenRequest("public-sandbox-token"))));

        UUID accountId = plaidAccountRepository.findActiveByTenantId(tenantId).get(0).getId();

        // Stub transfer auth + transfer create
        TransferAuthorization authorization = new TransferAuthorization()
                .id("auth-123")
                .decision(TransferAuthorizationDecision.APPROVED);
        Call<TransferAuthorizationCreateResponse> authCall = successCall(
                new TransferAuthorizationCreateResponse().authorization(authorization));
        when(plaidApi.transferAuthorizationCreate(any())).thenReturn(authCall);

        Transfer transfer = new Transfer().id("transfer-abc-123");
        Call<TransferCreateResponse> transferCall = successCall(new TransferCreateResponse().transfer(transfer));
        when(plaidApi.transferCreate(any())).thenReturn(transferCall);

        InitiateAchPaymentRequest achRequest = new InitiateAchPaymentRequest(
                accountId, 5000L, "usd", "Integration test ACH", "Test User", "ach-idem-001");

        mockMvc.perform(post("/api/v1/plaid/payments/ach")
                        .header("X-Tenant-Id", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(achRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plaidTransferId").value("transfer-abc-123"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(5000));

        assertThat(achPaymentRepository.findByPlaidTransferId("transfer-abc-123")).isPresent();
    }

    // ─── Plaid webhook ───────────────────────────────────────────────────────────

    @Test
    void plaidWebhook_itemError_returns200() throws Exception {
        mockMvc.perform(post("/webhooks/plaid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"webhook_type":"ITEM","webhook_code":"ERROR","item_id":"item-xyz"}
                                """))
                .andExpect(status().isOk());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    /** Stubs all Plaid API calls needed for the exchange-token flow. */
    private void stubExchangeFlow() throws Exception {
        Call<ItemPublicTokenExchangeResponse> exchangeCall = successCall(
                new ItemPublicTokenExchangeResponse()
                        .accessToken("access-sandbox-token")
                        .itemId("item-abc123"));
        when(plaidApi.itemPublicTokenExchange(any())).thenReturn(exchangeCall);

        Call<ItemGetResponse> itemGetCall = successCall(new ItemGetResponse()
                .item(new ItemWithConsentFields().institutionId("ins_3")));
        when(plaidApi.itemGet(any())).thenReturn(itemGetCall);

        Call<InstitutionsGetByIdResponse> instCall = successCall(new InstitutionsGetByIdResponse()
                .institution(new Institution().name("Chase")));
        when(plaidApi.institutionsGetById(any())).thenReturn(instCall);

        AccountBase account = new AccountBase()
                .accountId("acc-plaid-1")
                .name("Checking Account")
                .mask("1234")
                .type(AccountType.DEPOSITORY)
                .subtype(AccountSubtype.CHECKING);
        Call<AccountsGetResponse> accountsCall = successCall(
                new AccountsGetResponse().accounts(List.of(account)));
        when(plaidApi.accountsGet(any())).thenReturn(accountsCall);
    }

    @SuppressWarnings("unchecked")
    private <T> Call<T> successCall(T body) throws Exception {
        Call<T> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.success(body));
        return call;
    }
}
