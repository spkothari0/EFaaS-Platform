package com.efaas.tenant.service;

import com.efaas.common.dto.ApiKeyDTO;
import com.efaas.common.exception.InvalidApiKeyException;
import com.efaas.common.exception.TenantNotFoundException;
import com.efaas.tenant.domain.ApiKey;
import com.efaas.tenant.domain.Tenant;
import com.efaas.tenant.repository.ApiKeyRepository;
import com.efaas.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    ApiKeyRepository apiKeyRepository;

    @Mock
    TenantRepository tenantRepository;

    @InjectMocks
    ApiKeyService apiKeyService;

    private Tenant buildTenant() {
        return Tenant.builder()
                .name("Acme")
                .email("acme@example.com")
                .plan(Tenant.PlanTier.BASIC)
                .active(true)
                .build();
    }

    @Test
    void generateApiKey_returnsKeyWithCorrectPrefix() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(buildTenant()));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        ApiKeyDTO result = apiKeyService.generateApiKey(tenantId);

        assertThat(result.getKey()).startsWith("efaas_live_");
        assertThat(result.getMaskedKey()).contains("***");
    }

    @Test
    void generateApiKey_storesHashNotPlaintext() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(buildTenant()));
        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        when(apiKeyRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        ApiKeyDTO result = apiKeyService.generateApiKey(tenantId);

        ApiKey saved = captor.getValue();
        assertThat(saved.getKeyHash()).isNotBlank();
        assertThat(saved.getKeyHash()).doesNotContain("efaas_live_");
        assertThat(saved.getKeyHash()).isNotEqualTo(result.getKey());
    }

    @Test
    void generateApiKey_throwsTenantNotFoundException_forUnknownTenant() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiKeyService.generateApiKey(tenantId))
                .isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    void validateApiKey_returnsTenantId_forValidKey() {
        UUID tenantId = UUID.randomUUID();
        ApiKey apiKey = ApiKey.builder().tenantId(tenantId).active(true).build();
        when(apiKeyRepository.findByKeyHashAndActiveTrue(anyString())).thenReturn(Optional.of(apiKey));

        UUID result = apiKeyService.validateApiKey("efaas_live_somevalidkey");

        assertThat(result).isEqualTo(tenantId);
    }

    @Test
    void validateApiKey_throwsMalformed_forNullKey() {
        assertThatThrownBy(() -> apiKeyService.validateApiKey(null))
                .isInstanceOf(InvalidApiKeyException.class)
                .hasMessageContaining("malformed");
    }

    @Test
    void validateApiKey_throwsMalformed_forBlankKey() {
        assertThatThrownBy(() -> apiKeyService.validateApiKey("  "))
                .isInstanceOf(InvalidApiKeyException.class)
                .hasMessageContaining("malformed");
    }

    @Test
    void validateApiKey_throwsNotFound_forUnknownKey() {
        when(apiKeyRepository.findByKeyHashAndActiveTrue(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiKeyService.validateApiKey("efaas_live_unknownkey"))
                .isInstanceOf(InvalidApiKeyException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void validateApiKey_throwsExpired_forExpiredKey() {
        ApiKey expiredKey = ApiKey.builder()
                .tenantId(UUID.randomUUID())
                .active(true)
                .expiresAt(ZonedDateTime.now().minusDays(1))
                .build();
        when(apiKeyRepository.findByKeyHashAndActiveTrue(anyString())).thenReturn(Optional.of(expiredKey));

        assertThatThrownBy(() -> apiKeyService.validateApiKey("efaas_live_expiredkey"))
                .isInstanceOf(InvalidApiKeyException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void revokeApiKey_setsActiveToFalse() {
        UUID keyId = UUID.randomUUID();
        ApiKey apiKey = ApiKey.builder().id(keyId).tenantId(UUID.randomUUID()).active(true).build();
        when(apiKeyRepository.findById(keyId)).thenReturn(Optional.of(apiKey));
        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        when(apiKeyRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        apiKeyService.revokeApiKey(keyId);

        assertThat(captor.getValue().isActive()).isFalse();
    }

    @Test
    void revokeApiKey_throwsInvalidApiKeyException_forUnknownKey() {
        UUID keyId = UUID.randomUUID();
        when(apiKeyRepository.findById(keyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiKeyService.revokeApiKey(keyId))
                .isInstanceOf(InvalidApiKeyException.class);
    }

    @Test
    void getActiveKeysForTenant_returnsMappedDtos_withoutPlaintextKey() {
        UUID tenantId = UUID.randomUUID();
        ApiKey k1 = ApiKey.builder().tenantId(tenantId).maskedKey("efaas_li***12345").active(true).build();
        ApiKey k2 = ApiKey.builder().tenantId(tenantId).maskedKey("efaas_li***67890").active(true).build();
        when(apiKeyRepository.findByTenantIdAndActiveTrue(tenantId)).thenReturn(List.of(k1, k2));

        List<ApiKeyDTO> result = apiKeyService.getActiveKeysForTenant(tenantId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ApiKeyDTO::getKey).containsOnlyNulls();
    }
}
