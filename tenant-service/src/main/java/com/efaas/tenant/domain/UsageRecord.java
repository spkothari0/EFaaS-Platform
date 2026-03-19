package com.efaas.tenant.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Usage Record Entity - Tracks API calls per tenant per month.
 * Used for billing, rate limiting, and usage analytics.
 */
@Entity
@Table(name = "usage_records", indexes = {
    @Index(name = "idx_usage_tenant_month", columnList = "tenant_id,month", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageRecord {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false, columnDefinition = "UUID")
    private UUID tenantId;

    @Column(nullable = false, length = 7)  // YYYY-MM format
    private String month;  // YearMonth serialized as "2024-03"

    @Column(name = "api_calls_used", nullable = false)
    private long apiCallsUsed = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    /**
     * Increment the API call counter.
     */
    public void incrementApiCalls() {
        this.apiCallsUsed++;
        this.updatedAt = ZonedDateTime.now();
    }

    /**
     * Create a new usage record for a given tenant and month.
     */
    public static UsageRecord forTenantAndMonth(UUID tenantId, YearMonth yearMonth) {
        return UsageRecord.builder()
            .tenantId(tenantId)
            .month(yearMonth.toString())
            .apiCallsUsed(0)
            .build();
    }
}
