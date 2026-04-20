package com.efaas.common.event;

import com.efaas.common.enums.ServiceName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Published when a loan application is denied (score < 500).
 * Topic: lending-topic
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class LoanDeniedEvent extends DomainEvent {

    private UUID loanId;
    private int creditScore;
    private String reason;

    public LoanDeniedEvent(UUID tenantId, UUID loanId, int creditScore, String reason) {
        super();
        this.loanId = loanId;
        this.creditScore = creditScore;
        this.reason = reason;
        initializeEventMetadata(tenantId.toString(), ServiceName.LENDING_SERVICE);
    }

    @Override
    public String getEventName() { return "loan.denied"; }
}
