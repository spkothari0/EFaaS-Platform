package com.efaas.lending.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "repayment_installments", indexes = {
        @Index(name = "idx_installments_loan_id", columnList = "loan_id"),
        @Index(name = "idx_installments_status_due", columnList = "status, due_date")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepaymentInstallment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "principal_cents", nullable = false)
    private Long principalCents;

    @Column(name = "interest_cents", nullable = false)
    private Long interestCents;

    @Column(name = "total_cents", nullable = false)
    private Long totalCents;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private RepaymentStatus status;

    @Column(name = "paid_at")
    private ZonedDateTime paidAt;

    @Column(name = "stripe_payment_intent_id", length = 100)
    private String stripePaymentIntentId;
}
