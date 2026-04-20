package com.efaas.lending.kafka;

import com.efaas.common.event.LoanAppliedEvent;
import com.efaas.common.event.LoanApprovedEvent;
import com.efaas.common.event.LoanDeniedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoanEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.lending}")
    private String lendingTopic;

    public void publishLoanApplied(LoanAppliedEvent event) {
        kafkaTemplate.send(lendingTopic, event.getTenantId(), event);
        log.info("Published LoanAppliedEvent: loanId={}, score={}", event.getLoanId(), event.getCreditScore());
    }

    public void publishLoanApproved(LoanApprovedEvent event) {
        kafkaTemplate.send(lendingTopic, event.getTenantId(), event);
        log.info("Published LoanApprovedEvent: loanId={}, score={}", event.getLoanId(), event.getCreditScore());
    }

    public void publishLoanDenied(LoanDeniedEvent event) {
        kafkaTemplate.send(lendingTopic, event.getTenantId(), event);
        log.info("Published LoanDeniedEvent: loanId={}, score={}", event.getLoanId(), event.getCreditScore());
    }
}
