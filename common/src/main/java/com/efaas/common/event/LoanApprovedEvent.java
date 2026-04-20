package com.efaas.common.event;

import com.efaas.common.enums.ServiceName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Published when a loan is approved (score >= 700) or conditionally approved (500-699).
 * Topic: lending-topic
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class LoanApprovedEvent extends DomainEvent {

    private UUID loanId;
    private int creditScore;
    private long approvedAmountCents;
    private int termMonths;
    private long monthlyPaymentCents;

    public LoanApprovedEvent(UUID tenantId, UUID loanId, int creditScore,
                              long approvedAmountCents, int termMonths, long monthlyPaymentCents) {
        super();
        this.loanId = loanId;
        this.creditScore = creditScore;
        this.approvedAmountCents = approvedAmountCents;
        this.termMonths = termMonths;
        this.monthlyPaymentCents = monthlyPaymentCents;
        initializeEventMetadata(tenantId.toString(), ServiceName.LENDING_SERVICE);
    }

    @Override
    public String getEventName() { return "loan.approved"; }
}
