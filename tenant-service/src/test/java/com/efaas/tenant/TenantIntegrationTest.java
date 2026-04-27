package com.efaas.tenant;

import com.efaas.common.dto.ApiKeyDTO;
import com.efaas.common.dto.TenantDTO;
import com.efaas.common.exception.InvalidApiKeyException;
import com.efaas.common.exception.TenantNotFoundException;
import com.efaas.tenant.domain.Tenant;
import com.efaas.tenant.service.ApiKeyService;
import com.efaas.tenant.service.TenantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class TenantIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("test_tenants_db")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    TenantService tenantService;

    @Autowired
    ApiKeyService apiKeyService;

    @Test
    void tenantCrudLifecycle() {
        TenantDTO created = tenantService.createTenant("Acme Corp", "acme-it@example.com", Tenant.PlanTier.BASIC);
        assertThat(created.getId()).isNotNull();
        assertThat(created.isActive()).isTrue();

        TenantDTO fetched = tenantService.getTenant(created.getId());
        assertThat(fetched.getEmail()).isEqualTo("acme-it@example.com");

        TenantDTO updated = tenantService.updateTenant(created.getId(), "Acme Corp Inc", null, Tenant.PlanTier.PRO);
        assertThat(updated.getName()).isEqualTo("Acme Corp Inc");
        assertThat(updated.getPlan()).isEqualTo(TenantDTO.PlanTier.PRO);
        assertThat(updated.getEmail()).isEqualTo("acme-it@example.com");

        TenantDTO deactivated = tenantService.toggleTenantStatus(created.getId(), false);
        assertThat(deactivated.isActive()).isFalse();

        tenantService.deleteTenant(created.getId());
        assertThatThrownBy(() -> tenantService.getTenant(created.getId()))
                .isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    void getTenant_throwsTenantNotFoundException_forUnknownId() {
        assertThatThrownBy(() -> tenantService.getTenant(UUID.randomUUID()))
                .isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    void apiKeyGenerateValidateRevoke_fullCycle() {
        TenantDTO tenant = tenantService.createTenant("Beta Inc", "beta-it@example.com", Tenant.PlanTier.PRO);

        ApiKeyDTO generated = apiKeyService.generateApiKey(tenant.getId());
        assertThat(generated.getKey()).startsWith("efaas_live_");
        assertThat(generated.getMaskedKey()).contains("***");
        assertThat(generated.getKey()).isNotEqualTo(generated.getMaskedKey());

        UUID resolvedTenantId = apiKeyService.validateApiKey(generated.getKey());
        assertThat(resolvedTenantId).isEqualTo(tenant.getId());

        apiKeyService.revokeApiKey(generated.getId());

        assertThatThrownBy(() -> apiKeyService.validateApiKey(generated.getKey()))
                .isInstanceOf(InvalidApiKeyException.class);
    }
}
