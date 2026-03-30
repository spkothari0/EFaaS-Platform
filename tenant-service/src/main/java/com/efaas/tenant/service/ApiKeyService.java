package com.efaas.tenant.service;

import com.efaas.common.dto.ApiKeyDTO;
import com.efaas.common.dto.ApiKeyValidationResponse;
import com.efaas.common.event.ApiKeyGeneratedEvent;
import com.efaas.common.exception.InvalidApiKeyException;
import com.efaas.common.exception.TenantNotFoundException;
import com.efaas.tenant.domain.ApiKey;
import com.efaas.tenant.domain.Tenant;
import com.efaas.tenant.event.TenantEventPublisher;
import com.efaas.tenant.repository.ApiKeyRepository;
import com.efaas.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing API keys.
 * Handles generation, validation, hashing, and key lifecycle.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final TenantRepository tenantRepository;
    private final TenantEventPublisher eventPublisher;
    private static final String API_KEY_PREFIX = "efaas_live_";
    private static final int API_KEY_LENGTH = 32;  // 32 random bytes = 43 Base64 chars

    /**
     * Generate a new API key for a tenant.
     * Returns the plaintext key only once - it must be stored securely by the client.
     */
    public ApiKeyDTO generateApiKey(UUID tenantId) {
        tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException(tenantId.toString()));

        // Generate plaintext key (never stored)
        String plainKey = generatePlaintextKey();
        String keyHash = hashKey(plainKey);
        String maskedKey = ApiKey.maskKey(plainKey);

        ApiKey apiKey = ApiKey.builder()
            .tenantId(tenantId)
            .keyHash(keyHash)
            .maskedKey(maskedKey)
            .active(true)
            .build();

        ApiKey saved = apiKeyRepository.save(apiKey);
        log.info("API key generated for tenant: tenantId={}, keyId={}, maskedKey={}", tenantId, saved.getId(), maskedKey);

        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        eventPublisher.publishApiKeyGenerated(new ApiKeyGeneratedEvent(
            tenantId, saved.getId(), maskedKey, tenant.getPlan().name()));

        return ApiKeyDTO.builder()
            .id(saved.getId())
            .tenantId(saved.getTenantId())
            .key(plainKey)  // Return plaintext only once
            .maskedKey(saved.getMaskedKey())
            .active(saved.isActive())
            .createdAt(saved.getCreatedAt())
            .expiresAt(saved.getExpiresAt())
            .build();
    }

    /**
     * Validate an API key.
     * Returns the tenantId if valid, throws exception if invalid.
     */
    @Transactional(readOnly = true)
    public UUID validateApiKey(String plainKey) {
        if (plainKey == null || plainKey.isBlank()) {
            throw InvalidApiKeyException.malformed();
        }

        String keyHash = hashKey(plainKey);
        ApiKey apiKey = apiKeyRepository.findByKeyHashAndActiveTrue(keyHash)
            .orElseThrow(InvalidApiKeyException::notFound);

        if (!apiKey.isValid()) {
            throw InvalidApiKeyException.expired();
        }

        log.debug("API key validated for tenant: tenantId={}", apiKey.getTenantId());
        return apiKey.getTenantId();
    }

    /**
     * Validate an API key and return the full tenant context.
     * Used by the API Gateway for rate limiting and header injection.
     */
    @Transactional(readOnly = true)
    public ApiKeyValidationResponse validateForGateway(String plainKey) {
        if (plainKey == null || plainKey.isBlank()) {
            throw InvalidApiKeyException.malformed();
        }

        String keyHash = hashKey(plainKey);
        ApiKey apiKey = apiKeyRepository.findByKeyHashAndActiveTrue(keyHash)
            .orElseThrow(InvalidApiKeyException::notFound);

        if (!apiKey.isValid()) {
            throw InvalidApiKeyException.expired();
        }

        Tenant tenant = tenantRepository.findById(apiKey.getTenantId())
            .orElseThrow(() -> new TenantNotFoundException(apiKey.getTenantId().toString()));

        log.debug("Gateway API key validated: tenantId={}, plan={}", tenant.getId(), tenant.getPlan());

        return ApiKeyValidationResponse.builder()
            .tenantId(tenant.getId())
            .planTier(tenant.getPlan().name())
            .rateLimitPerMinute(tenant.getRateLimitPerMinute())
            .build();
    }

    /**
     * Revoke (deactivate) an API key.
     */
    public void revokeApiKey(UUID keyId) {
        ApiKey apiKey = apiKeyRepository.findById(keyId)
            .orElseThrow(() -> new InvalidApiKeyException("API key not found"));

        apiKey.setActive(false);
        apiKeyRepository.save(apiKey);
        log.info("API key revoked: keyId={}, tenantId={}", keyId, apiKey.getTenantId());
    }

    /**
     * Get all active API keys for a tenant.
     */
    @Transactional(readOnly = true)
    public List<ApiKeyDTO> getActiveKeysForTenant(UUID tenantId) {
        return apiKeyRepository.findByTenantIdAndActiveTrue(tenantId)
            .stream()
            .map(this::toDTO)
            .toList();
    }

    /**
     * Generate a random, URL-safe plaintext API key.
     */
    private String generatePlaintextKey() {
        SecureRandom random = new SecureRandom();
        byte[] randomBytes = new byte[API_KEY_LENGTH];
        random.nextBytes(randomBytes);
        return API_KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Hash an API key using SHA-256.
     * Always hash keys before storing or comparing.
     */
    private String hashKey(String plainKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plainKey.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            throw new RuntimeException("Cryptographic algorithm not available", e);
        }
    }

    /**
     * Convert ApiKey entity to DTO.
     * Never includes the plaintext key in DTOs.
     */
    private ApiKeyDTO toDTO(ApiKey apiKey) {
        return ApiKeyDTO.builder()
            .id(apiKey.getId())
            .tenantId(apiKey.getTenantId())
            .maskedKey(apiKey.getMaskedKey())
            .active(apiKey.isActive())
            .createdAt(apiKey.getCreatedAt())
            .expiresAt(apiKey.getExpiresAt())
            .build();
    }
}
