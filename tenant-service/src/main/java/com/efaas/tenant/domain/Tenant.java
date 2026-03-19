package com.efaas.tenant.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Tenant Entity - Represents a customer/business using the EFaaS platform.
 * Each tenant is isolated and has their own API keys, rate limits, and data.
 */
@Entity
@Table(name = "tenants", indexes = {
    @Index(name = "idx_tenants_email", columnList = "email", unique = true),
    @Index(name = "idx_tenants_active", columnList = "active")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 255, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanTier plan;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private ZonedDateTime updatedAt;

    public enum PlanTier {
        FREE,      // 10 req/min
        BASIC,     // 100 req/min
        PRO        // 1000 req/min
    }

    public int getRateLimitPerMinute() {
        return switch (this.plan) {
            case FREE -> 10;
            case BASIC -> 100;
            case PRO -> 1000;
        };
    }

    public boolean isValid() {
        return this.active;
    }
}
