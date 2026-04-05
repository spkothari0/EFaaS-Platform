package com.efaas.common.event;

import com.efaas.common.enums.ServiceName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Published when a Stripe charge.refunded webhook is received.
 * Topic: payments-topic
 * Consumed by: Lending Service (reverse repayment match), Fraud Service
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class PaymentRefundedEvent extends DomainEvent {

    private UUID paymentId;
    private String stripePaymentIntentId;
    private Long amount;
    private String currency;

    public PaymentRefundedEvent(UUID paymentId, String stripePaymentIntentId,
                                String tenantId, Long amount, String currency) {
        super();
        this.paymentId = paymentId;
        this.stripePaymentIntentId = stripePaymentIntentId;
        this.amount = amount;
        this.currency = currency;
        initializeEventMetadata(tenantId, ServiceName.PAYMENT_SERVICE);
    }

    @Override
    public String getEventName() {
        return "payment.refunded";
    }
}
