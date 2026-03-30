package com.efaas.tenant.event;

import com.efaas.common.event.ApiKeyGeneratedEvent;
import com.efaas.common.event.TenantCreatedEvent;
import com.efaas.tenant.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes tenant domain events to Kafka.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TenantEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishTenantCreated(TenantCreatedEvent event) {
        kafkaTemplate.send(KafkaConfig.TOPIC_TENANT_CREATED, event.getTenantId(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish TenantCreatedEvent: tenantId={}", event.getTenantId(), ex);
                } else {
                    log.info("Published TenantCreatedEvent: tenantId={}, offset={}",
                        event.getTenantId(), result.getRecordMetadata().offset());
                }
            });
    }

    public void publishApiKeyGenerated(ApiKeyGeneratedEvent event) {
        kafkaTemplate.send(KafkaConfig.TOPIC_APIKEY_GENERATED, event.getTenantId(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish ApiKeyGeneratedEvent: tenantId={}", event.getTenantId(), ex);
                } else {
                    log.info("Published ApiKeyGeneratedEvent: tenantId={}, keyId={}, offset={}",
                        event.getTenantId(), event.getKeyId(), result.getRecordMetadata().offset());
                }
            });
    }
}
