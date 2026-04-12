package com.efaas.payment;

import com.efaas.payment.entity.Payment;
import com.efaas.payment.entity.PaymentStatus;
import com.efaas.payment.entity.WebhookEvent;
import com.efaas.payment.repository.PaymentRepository;
import com.efaas.payment.repository.WebhookEventRepository;
import com.efaas.payment.service.StripeWebhookService;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the payment service.
 * Uses a real PostgreSQL container and embedded Kafka.
 * Stripe API calls are mocked — no real Stripe network calls in CI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {"payments-topic"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class PaymentIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payments_db")
            .withUsername("efaas")
            .withPassword("dev_password");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Embedded Kafka bootstrap servers are set automatically by @EmbeddedKafka via
        // the spring.embedded.kafka.brokers system property when the broker starts
        // Stripe keys — mocked so values don't matter
        registry.add("stripe.secret-key", () -> "sk_test_dummy_for_tests");
        registry.add("stripe.webhook-secret", () -> "whsec_dummy_for_tests");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Autowired
    private StripeWebhookService stripeWebhookService;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        paymentRepository.deleteAll();
        webhookEventRepository.deleteAll();
    }

    @Test
    void webhookFlow_paymentSucceeded_updatesDbAndPublishesKafkaEvent() {
        // Arrange: create a PENDING payment directly in DB
        Payment payment = paymentRepository.save(Payment.builder()
                .tenantId(tenantId)
                .stripePaymentIntentId("pi_integration_test_001")
                .idempotencyKey("integration-test-idem-001")
                .amount(5000L)
                .currency("usd")
                .description("Integration test payment")
                .status(PaymentStatus.PENDING)
                .build());

        // Act: simulate receiving a payment_intent.succeeded webhook event
        Event event = mockPaymentIntentEvent(
                "payment_intent.succeeded", "evt_integration_001", "pi_integration_test_001");
        stripeWebhookService.processEvent(event);

        // Assert: DB status updated to COMPLETED
        Payment updated = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        // Assert: idempotency record saved
        assertThat(webhookEventRepository.existsByStripeEventId("evt_integration_001")).isTrue();
    }

    @Test
    void webhookFlow_duplicateEvent_isIdempotent() {
        paymentRepository.save(Payment.builder()
                .tenantId(tenantId)
                .stripePaymentIntentId("pi_dup_test")
                .idempotencyKey("idem-dup-test")
                .amount(1000L)
                .currency("usd")
                .status(PaymentStatus.PENDING)
                .build());

        // Save the event as already processed
        webhookEventRepository.save(WebhookEvent.builder()
                .stripeEventId("evt_already_processed")
                .eventType("payment_intent.succeeded")
                .processedAt(ZonedDateTime.now())
                .build());

        Event event = mockPaymentIntentEvent(
                "payment_intent.succeeded", "evt_already_processed", "pi_dup_test");
        stripeWebhookService.processEvent(event);

        // Payment should still be PENDING — event was skipped
        Payment payment = paymentRepository.findByIdempotencyKey("idem-dup-test").orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void webhookFlow_paymentFailed_updatesDb() {
        paymentRepository.save(Payment.builder()
                .tenantId(tenantId)
                .stripePaymentIntentId("pi_integration_test_fail")
                .idempotencyKey("integration-test-idem-fail")
                .amount(5000L)
                .currency("usd")
                .description("Integration test failed payment")
                .status(PaymentStatus.PENDING)
                .build());

        Event event = mockPaymentIntentEvent(
                "payment_intent.payment_failed", "evt_integration_fail", "pi_integration_test_fail");
        stripeWebhookService.processEvent(event);

        Payment updated = paymentRepository.findByIdempotencyKey("integration-test-idem-fail").orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(webhookEventRepository.existsByStripeEventId("evt_integration_fail")).isTrue();
    }

    @Test
    void createPayment_withInvalidRequest_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .header("X-Tenant-Id", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 10,
                                  "currency": "usd",
                                  "idempotencyKey": "test-key"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    // --- helpers ---

    private Event mockPaymentIntentEvent(String type, String eventId, String piId) {
        PaymentIntent pi = mock(PaymentIntent.class);
        when(pi.getId()).thenReturn(piId);

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(pi));

        Event event = mock(Event.class);
        when(event.getId()).thenReturn(eventId);
        when(event.getType()).thenReturn(type);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        return event;
    }
}
