package com.efaas.payment.kafka;

import com.efaas.common.event.AchPaymentInitiatedEvent;
import com.efaas.common.event.BankAccountLinkedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlaidEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.plaid}")
    private String plaidTopic;

    // Use tenantId as message key → all events for a tenant go to the same partition

    public void publishBankAccountLinked(BankAccountLinkedEvent event) {
        kafkaTemplate.send(plaidTopic, event.getTenantId(), event);
        log.info("Published BankAccountLinkedEvent for item {} to topic {}",
                event.getPlaidItemId(), plaidTopic);
    }

    public void publishAchPaymentInitiated(AchPaymentInitiatedEvent event) {
        kafkaTemplate.send(plaidTopic, event.getTenantId(), event);
        log.info("Published AchPaymentInitiatedEvent for payment {} to topic {}",
                event.getAchPaymentId(), plaidTopic);
    }
}
