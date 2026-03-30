package com.efaas.tenant.kafka;

import com.efaas.common.dto.ApiKeyDTO;
import com.efaas.common.dto.TenantDTO;
import com.efaas.tenant.domain.Tenant;
import com.efaas.tenant.service.ApiKeyService;
import com.efaas.tenant.service.TenantService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying that domain events are published to Kafka
 * when tenants and API keys are created.
 *
 * Uses Testcontainers for PostgreSQL and Kafka (no mocks, full round-trip).
 */
@SpringBootTest
@Testcontainers
class TenantKafkaIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("test_tenants_db")
        .withUsername("test")
        .withPassword("test");

    @Container
    @SuppressWarnings("resource")
    static final KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    TenantService tenantService;

    @Autowired
    ApiKeyService apiKeyService;

    @Test
    void shouldPublishTenantCreatedEventOnTenantCreation() {
        TenantDTO tenant = tenantService.createTenant("Acme Corp", "acme@example.com", Tenant.PlanTier.BASIC);

        assertThat(tenant.getId()).isNotNull();

        try (Consumer<String, String> consumer = buildConsumer()) {
            consumer.subscribe(Collections.singletonList("efaas.tenant.created"));
            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));

            assertThat(records.count()).isGreaterThanOrEqualTo(1);

            boolean found = false;
            for (var record : records) {
                if (record.key().equals(tenant.getId().toString())) {
                    assertThat(record.value()).contains("tenant.created");
                    assertThat(record.value()).contains("Acme Corp");
                    found = true;
                    break;
                }
            }
            assertThat(found).as("TenantCreatedEvent not found in topic").isTrue();
        }
    }

    @Test
    void shouldPublishApiKeyGeneratedEventOnKeyCreation() {
        TenantDTO tenant = tenantService.createTenant("Beta Inc", "beta@example.com", Tenant.PlanTier.PRO);
        ApiKeyDTO apiKey = apiKeyService.generateApiKey(tenant.getId());

        assertThat(apiKey.getKey()).startsWith("efaas_live_");

        try (Consumer<String, String> consumer = buildConsumer()) {
            consumer.subscribe(Collections.singletonList("efaas.apikey.generated"));
            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));

            assertThat(records.count()).isGreaterThanOrEqualTo(1);

            boolean found = false;
            for (var record : records) {
                if (record.key().equals(tenant.getId().toString())) {
                    assertThat(record.value()).contains("apikey.generated");
                    assertThat(record.value()).contains(apiKey.getId().toString());
                    found = true;
                    break;
                }
            }
            assertThat(found).as("ApiKeyGeneratedEvent not found in topic").isTrue();
        }
    }

    private Consumer<String, String> buildConsumer() {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
            kafka.getBootstrapServers(), "test-consumer-" + System.nanoTime(), "false");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
    }
}
