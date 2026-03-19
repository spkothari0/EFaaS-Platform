package com.efaas.tenant.repository;

import com.efaas.tenant.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Tenant entity.
 * Provides CRUD operations and custom queries for tenant management.
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByEmailIgnoreCase(String email);

    long countByActive(boolean active);
}
