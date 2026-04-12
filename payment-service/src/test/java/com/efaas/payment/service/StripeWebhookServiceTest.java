package com.efaas.payment.service;

import com.efaas.payment.entity.Payment;
import com.efaas.payment.entity.PaymentStatus;
import com.efaas.payment.entity.WebhookEvent;
import com.efaas.payment.kafka.PaymentEventPublisher;
import com.efaas.payment.repository.PaymentRepository;
import com.efaas.payment.repository.WebhookEventRepository;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripeWebhookServiceTest {

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentEventPublisher eventPublisher;

    @InjectMocks
    private StripeWebhookService stripeWebhookService;

    private Payment completedPayment;
    private static final String STRIPE_PI_ID = "pi_test_123";
    private static final String STRIPE_EVENT_ID = "evt_test_456";

    @BeforeEach
    void setUp() {
        completedPayment = Payment.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .stripePaymentIntentId(STRIPE_PI_ID)
                .amount(2000L)
                .currency("usd")
                .status(PaymentStatus.PENDING)
                .build();
    }

    @Test
    void processEvent_whenDuplicateEventId_skipsProcessing() {
        Event event = mockEvent("payment_intent.succeeded", STRIPE_EVENT_ID);
        when(webhookEventRepository.existsByStripeEventId(STRIPE_EVENT_ID)).thenReturn(true);

        stripeWebhookService.processEvent(event);

        verify(paymentRepository, never()).findByStripePaymentIntentId(any());
        verify(webhookEventRepository, never()).save(any());
    }

    @Test
    void processEvent_onPaymentSucceeded_updatesStatusAndPublishesEvent() {
        Event event = mockEventWithPaymentIntent("payment_intent.succeeded", STRIPE_EVENT_ID, STRIPE_PI_ID);
        when(webhookEventRepository.existsByStripeEventId(STRIPE_EVENT_ID)).thenReturn(false);
        when(paymentRepository.findByStripePaymentIntentId(STRIPE_PI_ID)).thenReturn(Optional.of(completedPayment));
        when(paymentRepository.save(any())).thenReturn(completedPayment);

        stripeWebhookService.processEvent(event);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        verify(eventPublisher).publishPaymentCompleted(any(Payment.class));

        ArgumentCaptor<WebhookEvent> webhookCaptor = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookEventRepository).save(webhookCaptor.capture());
        assertThat(webhookCaptor.getValue().getStripeEventId()).isEqualTo(STRIPE_EVENT_ID);
    }

    @Test
    void processEvent_onPaymentFailed_updatesStatusAndPublishesEvent() {
        Event event = mockEventWithPaymentIntent("payment_intent.payment_failed", STRIPE_EVENT_ID, STRIPE_PI_ID);
        when(webhookEventRepository.existsByStripeEventId(STRIPE_EVENT_ID)).thenReturn(false);
        when(paymentRepository.findByStripePaymentIntentId(STRIPE_PI_ID)).thenReturn(Optional.of(completedPayment));
        when(paymentRepository.save(any())).thenReturn(completedPayment);

        stripeWebhookService.processEvent(event);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);

        verify(eventPublisher).publishPaymentFailed(any(Payment.class));
    }

    @Test
    void processEvent_whenPaymentNotFoundForIntent_throwsIllegalStateException() {
        Event event = mockEventWithPaymentIntent("payment_intent.succeeded", STRIPE_EVENT_ID, STRIPE_PI_ID);
        when(webhookEventRepository.existsByStripeEventId(STRIPE_EVENT_ID)).thenReturn(false);
        when(paymentRepository.findByStripePaymentIntentId(STRIPE_PI_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stripeWebhookService.processEvent(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(STRIPE_PI_ID);

        verify(paymentRepository, never()).save(any());
        verify(eventPublisher, never()).publishPaymentCompleted(any());
        // WebhookEvent is NOT saved — transaction rolls back on exception
        verify(webhookEventRepository, never()).save(any(WebhookEvent.class));
    }

    @Test
    void processEvent_onChargeRefunded_updatesStatusToRefunded() {
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .stripePaymentIntentId(STRIPE_PI_ID)
                .amount(2000L)
                .currency("usd")
                .status(PaymentStatus.COMPLETED)
                .build();

        Event event = mockEventWithCharge("charge.refunded", STRIPE_EVENT_ID, STRIPE_PI_ID);
        when(webhookEventRepository.existsByStripeEventId(STRIPE_EVENT_ID)).thenReturn(false);
        when(paymentRepository.findByStripePaymentIntentId(STRIPE_PI_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenReturn(payment);

        stripeWebhookService.processEvent(event);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(eventPublisher).publishPaymentRefunded(any(Payment.class));
    }

    @Test
    void processEvent_onChargeRefunded_whenAlreadyRefunded_doesNotSaveAgain() {
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .stripePaymentIntentId(STRIPE_PI_ID)
                .amount(2000L)
                .currency("usd")
                .status(PaymentStatus.REFUNDED)
                .build();

        Event event = mockEventWithCharge("charge.refunded", STRIPE_EVENT_ID, STRIPE_PI_ID);
        when(webhookEventRepository.existsByStripeEventId(STRIPE_EVENT_ID)).thenReturn(false);
        when(paymentRepository.findByStripePaymentIntentId(STRIPE_PI_ID)).thenReturn(Optional.of(payment));

        stripeWebhookService.processEvent(event);

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void processEvent_onUnknownEventType_savesEventButDoesNotUpdatePayment() {
        Event event = mockEvent("some.unknown.event", STRIPE_EVENT_ID);
        when(webhookEventRepository.existsByStripeEventId(STRIPE_EVENT_ID)).thenReturn(false);

        stripeWebhookService.processEvent(event);

        verify(paymentRepository, never()).findByStripePaymentIntentId(any());
        verify(webhookEventRepository).save(any());
    }

    // --- helpers ---

    private Event mockEvent(String type, String eventId) {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(eventId);
        // getType() is only called after the idempotency check — use lenient to avoid
        // UnnecessaryStubbingException when event is skipped before getType() is reached
        lenient().when(event.getType()).thenReturn(type);
        return event;
    }

    private Event mockEventWithPaymentIntent(String type, String eventId, String piId) {
        Event event = mockEvent(type, eventId);

        PaymentIntent pi = mock(PaymentIntent.class);
        when(pi.getId()).thenReturn(piId);

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(pi));
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        return event;
    }

    private Event mockEventWithCharge(String type, String eventId, String piId) {
        Event event = mockEvent(type, eventId);

        Charge charge = mock(Charge.class);
        when(charge.getPaymentIntent()).thenReturn(piId);

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(charge));
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        return event;
    }
}
