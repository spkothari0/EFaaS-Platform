package com.efaas.tenant.repository;

import com.efaas.tenant.domain.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ApiKey entity.
 * Provides CRUD operations and queries for API key management.
 */
@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    /**
     * Find an active API key by its SHA-256 hash.
     * Used during authentication to validate incoming API keys.
     */
    Optional<ApiKey> findByKeyHashAndActiveTrue(String keyHash);

    /**
     * Find all active API keys for a tenant.
     */
    List<ApiKey> findByTenantIdAndActiveTrue(UUID tenantId);

    /**
     * Find all API keys (active or inactive) for a tenant.
     */
    List<ApiKey> findByTenantId(UUID tenantId);
}
