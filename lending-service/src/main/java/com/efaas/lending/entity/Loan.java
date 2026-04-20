package com.efaas.lending.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "loans", indexes = {
        @Index(name = "idx_loans_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_loans_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "applicant_user_id", nullable = false, length = 255)
    private String applicantUserId;

    @Column(name = "plaid_account_id", nullable = false)
    private UUID plaidAccountId;

    @Column(name = "principal_amount_cents", nullable = false)
    private Long principalAmountCents;

    @Column(name = "annual_interest_rate", nullable = false)
    private Double annualInterestRate;

    @Column(name = "term_months", nullable = false)
    private Integer termMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private LoanStatus status;

    @Column(name = "credit_score", nullable = false)
    private Integer creditScore;

    @Column(name = "monthly_payment_cents")
    private Long monthlyPaymentCents;

    @Column(name = "purpose", length = 500)
    private String purpose;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Column(name = "disbursed_at")
    private ZonedDateTime disbursedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;
}
