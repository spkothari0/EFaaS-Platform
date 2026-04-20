package com.efaas.lending.kafka;

import com.efaas.common.event.DomainEvent;
import com.efaas.common.event.PaymentCompletedEvent;
import com.efaas.lending.service.LoanRepaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final LoanRepaymentService loanRepaymentService;

    @KafkaListener(topics = "${kafka.topics.payments:payments-topic}", groupId = "lending-service")
    public void onPaymentEvent(DomainEvent event) {
        if (event instanceof PaymentCompletedEvent paymentEvent) {
            log.info("Received PaymentCompletedEvent: paymentIntentId={}, tenantId={}",
                    paymentEvent.getStripePaymentIntentId(), paymentEvent.getTenantId());
            loanRepaymentService.matchPayment(paymentEvent);
        } else {
            log.debug("Ignored event type: {}", event.getClass().getSimpleName());
        }
    }
}
