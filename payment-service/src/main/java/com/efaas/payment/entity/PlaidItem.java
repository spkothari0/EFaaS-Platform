package com.efaas.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Represents one Plaid Item — a single bank connection for an end-user.
 * One Item can have multiple bank accounts (PlaidAccount).
 */
@Entity
@Table(name = "plaid_items", indexes = {
        @Index(name = "idx_plaid_items_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_plaid_items_plaid_item_id", columnList = "plaid_item_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaidItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "plaid_item_id", nullable = false, length = 100)
    private String plaidItemId;

    // TODO: encrypt at rest in production (AES-256 or KMS)
    @Column(name = "access_token", nullable = false, length = 200)
    private String accessToken;

    @Column(name = "institution_id", length = 100)
    private String institutionId;

    @Column(name = "institution_name", length = 200)
    private String institutionName;

    /** ACTIVE | ERROR | REMOVED */
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;
}
