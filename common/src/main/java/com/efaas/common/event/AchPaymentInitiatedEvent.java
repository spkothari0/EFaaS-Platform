package com.efaas.common.event;

import com.efaas.common.enums.ServiceName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Published when an ACH bank transfer is initiated via Plaid Transfer API.
 * Topic: plaid-topic
 * Consumed by: Fraud Service (velocity checks), Lending Service (repayment matching)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class AchPaymentInitiatedEvent extends DomainEvent {

    private UUID achPaymentId;
    private String plaidTransferId;
    private Long amount;
    private String currency;

    public AchPaymentInitiatedEvent(UUID tenantId, UUID achPaymentId, String plaidTransferId, Long amount, String currency) {
        super();
        this.achPaymentId = achPaymentId;
        this.plaidTransferId = plaidTransferId;
        this.amount = amount;
        this.currency = currency;
        initializeEventMetadata(tenantId.toString(), ServiceName.PAYMENT_SERVICE);
    }

    @Override
    public String getEventName() {
        return "ach-payment.initiated";
    }
}
