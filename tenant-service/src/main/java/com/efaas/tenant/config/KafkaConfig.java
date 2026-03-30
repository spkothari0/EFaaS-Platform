package com.efaas.tenant.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic definitions for the Tenant Service.
 * Spring Boot auto-configures KafkaTemplate via application.yml producer settings.
 */
@Configuration
public class KafkaConfig {

    public static final String TOPIC_TENANT_CREATED = "efaas.tenant.created";
    public static final String TOPIC_APIKEY_GENERATED = "efaas.apikey.generated";

    @Bean
    public NewTopic tenantCreatedTopic() {
        return TopicBuilder.name(TOPIC_TENANT_CREATED)
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic apiKeyGeneratedTopic() {
        return TopicBuilder.name(TOPIC_APIKEY_GENERATED)
            .partitions(3)
            .replicas(1)
            .build();
    }
}
