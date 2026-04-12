package com.efaas.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Represents an ACH bank transfer initiated via the Plaid Transfer API.
 * Amount is stored in smallest currency unit (cents) — converted to dollars when calling Plaid.
 */
@Entity
@Table(name = "ach_payments", indexes = {
        @Index(name = "idx_ach_payments_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_ach_payments_plaid_transfer_id", columnList = "plaid_transfer_id"),
        @Index(name = "idx_ach_payments_idempotency_key", columnList = "idempotency_key", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AchPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** References our internal PlaidAccount.id (not Plaid's account ID) */
    @Column(name = "plaid_account_db_id", nullable = false)
    private UUID plaidAccountDbId;

    /** Plaid Transfer ID returned by /transfer/create */
    @Column(name = "plaid_transfer_id", length = 100)
    private String plaidTransferId;

    /** Plaid Authorization ID from /transfer/authorization/create */
    @Column(name = "plaid_authorization_id", length = 100)
    private String plaidAuthorizationId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    /** Amount in cents — e.g., 5000 = $50.00 */
    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AchPaymentStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;
}
