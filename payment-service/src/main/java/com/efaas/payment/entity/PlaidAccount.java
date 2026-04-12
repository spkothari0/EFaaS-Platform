package com.efaas.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * A single bank account returned by Plaid for a linked PlaidItem.
 * tenantId is stored directly (denormalized) for efficient tenant-scoped queries.
 */
@Entity
@Table(name = "plaid_accounts", indexes = {
        @Index(name = "idx_plaid_accounts_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_plaid_accounts_plaid_account_id", columnList = "plaid_account_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaidAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plaid_item_id", nullable = false)
    private PlaidItem plaidItem;

    /** Plaid's own account identifier — used when initiating transfers */
    @Column(name = "plaid_account_id", nullable = false, length = 100)
    private String plaidAccountId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** Last 4 digits of the account number */
    @Column(name = "mask", length = 10)
    private String mask;

    /** depository | credit | investment | loan | other */
    @Column(name = "type", length = 50)
    private String type;

    /** checking | savings | credit card | etc. */
    @Column(name = "subtype", length = 50)
    private String subtype;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;
}
