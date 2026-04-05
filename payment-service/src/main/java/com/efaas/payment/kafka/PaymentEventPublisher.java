package com.efaas.payment.kafka;

import com.efaas.common.event.PaymentCompletedEvent;
import com.efaas.common.event.PaymentFailedEvent;
import com.efaas.common.event.PaymentRefundedEvent;
import com.efaas.payment.entity.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.payments}")
    private String paymentsTopic;

    // Use tenantId as message key → all events for a tenant go to the same partition (ordering guarantee)

    public void publishPaymentCompleted(Payment payment) {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                payment.getId(),
                payment.getStripePaymentIntentId(),
                payment.getTenantId().toString(),
                payment.getAmount(),
                payment.getCurrency()
        );
        kafkaTemplate.send(paymentsTopic, payment.getTenantId().toString(), event);
        log.info("Published PaymentCompletedEvent for payment {} to topic {}", payment.getId(), paymentsTopic);
    }

    public void publishPaymentFailed(Payment payment) {
        PaymentFailedEvent event = new PaymentFailedEvent(
                payment.getId(),
                payment.getStripePaymentIntentId(),
                payment.getTenantId().toString(),
                payment.getAmount(),
                payment.getCurrency()
        );
        kafkaTemplate.send(paymentsTopic, payment.getTenantId().toString(), event);
        log.info("Published PaymentFailedEvent for payment {} to topic {}", payment.getId(), paymentsTopic);
    }

    public void publishPaymentRefunded(Payment payment) {
        PaymentRefundedEvent event = new PaymentRefundedEvent(
                payment.getId(),
                payment.getStripePaymentIntentId(),
                payment.getTenantId().toString(),
                payment.getAmount(),
                payment.getCurrency()
        );
        kafkaTemplate.send(paymentsTopic, payment.getTenantId().toString(), event);
        log.info("Published PaymentRefundedEvent for payment {} to topic {}", payment.getId(), paymentsTopic);
    }
}
