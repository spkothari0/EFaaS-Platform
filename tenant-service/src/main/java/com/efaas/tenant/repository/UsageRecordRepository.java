package com.efaas.tenant.repository;

import com.efaas.tenant.domain.UsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for UsageRecord entity.
 * Provides access to usage metrics per tenant per month.
 */
@Repository
public interface UsageRecordRepository extends JpaRepository<UsageRecord, UUID> {

    /**
     * Find usage record for a specific tenant and month.
     * Month format: "YYYY-MM" (e.g., "2024-03")
     */
    Optional<UsageRecord> findByTenantIdAndMonth(UUID tenantId, String month);
}
