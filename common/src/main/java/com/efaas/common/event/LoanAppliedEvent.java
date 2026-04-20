package com.efaas.common.event;

import com.efaas.common.enums.ServiceName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Published when a loan application is submitted and scored.
 * Topic: lending-topic
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class LoanAppliedEvent extends DomainEvent {

    private UUID loanId;
    private int creditScore;
    private long requestedAmountCents;
    private String status;

    public LoanAppliedEvent(UUID tenantId, UUID loanId, int creditScore,
                             long requestedAmountCents, String status) {
        super();
        this.loanId = loanId;
        this.creditScore = creditScore;
        this.requestedAmountCents = requestedAmountCents;
        this.status = status;
        initializeEventMetadata(tenantId.toString(), ServiceName.LENDING_SERVICE);
    }

    @Override
    public String getEventName() { return "loan.applied"; }
}
