package com.efaas.payment.service;

import com.efaas.payment.entity.PaymentStatus;
import com.efaas.payment.entity.WebhookEvent;
import com.efaas.payment.kafka.PaymentEventPublisher;
import com.efaas.payment.repository.PaymentRepository;
import com.efaas.payment.repository.WebhookEventRepository;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookEventRepository webhookEventRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentEventPublisher eventPublisher;

    @Transactional
    public void processEvent(Event event) {
        // Idempotency guard: skip if already processed (Stripe retries on non-2xx)
        if (webhookEventRepository.existsByStripeEventId(event.getId())) {
            log.info("Skipping already-processed webhook event: {}", event.getId());
            return;
        }

        log.info("Processing Stripe webhook event: {} ({})", event.getId(), event.getType());

        switch (event.getType()) {
            case "payment_intent.succeeded" -> handlePaymentSucceeded(event);
            case "payment_intent.payment_failed" -> handlePaymentFailed(event);
            case "charge.refunded" -> handleChargeRefunded(event);
            default -> log.debug("Unhandled Stripe event type: {}", event.getType());
        }

        // Record the event to prevent reprocessing
        webhookEventRepository.save(WebhookEvent.builder()
                .stripeEventId(event.getId())
                .eventType(event.getType())
                .processedAt(ZonedDateTime.now())
                .build());
    }

    private void handlePaymentSucceeded(Event event) {
        String piId = getPaymentIntentId(event)
                .orElseThrow(() -> new IllegalStateException("Could not extract PaymentIntent ID from event: " + event.getId()));
        paymentRepository.findByStripePaymentIntentId(piId)
                .ifPresentOrElse(
                        payment -> {
                            payment.setStatus(PaymentStatus.COMPLETED);
                            paymentRepository.save(payment);
                            eventPublisher.publishPaymentCompleted(payment);
                            log.info("Payment {} marked COMPLETED, event published", payment.getId());
                        },
                        () -> { throw new IllegalStateException("No payment record found for Stripe PI: " + piId); }
                );
    }

    private void handlePaymentFailed(Event event) {
        String piId = getPaymentIntentId(event)
                .orElseThrow(() -> new IllegalStateException("Could not extract PaymentIntent ID from event: " + event.getId()));
        paymentRepository.findByStripePaymentIntentId(piId)
                .ifPresentOrElse(
                        payment -> {
                            payment.setStatus(PaymentStatus.FAILED);
                            paymentRepository.save(payment);
                            eventPublisher.publishPaymentFailed(payment);
                            log.info("Payment {} marked FAILED, event published", payment.getId());
                        },
                        () -> { throw new IllegalStateException("No payment record found for Stripe PI: " + piId); }
                );
    }

    private void handleChargeRefunded(Event event) {
        // charge.refunded contains a Charge object; extract the payment_intent from it
        Optional<String> piId = event.getDataObjectDeserializer()
                .getObject()
                .map(obj -> {
                    if (obj instanceof com.stripe.model.Charge charge) {
                        return charge.getPaymentIntent();
                    }
                    return null;
                });

        piId.ifPresent(id -> {
            paymentRepository.findByStripePaymentIntentId(id).ifPresentOrElse(
                    payment -> {
                        if (payment.getStatus() != PaymentStatus.REFUNDED) {
                            payment.setStatus(PaymentStatus.REFUNDED);
                            paymentRepository.save(payment);
                            log.info("Payment {} marked REFUNDED via charge.refunded event", payment.getId());
                        }
                    },
                    () -> log.warn("No payment record found for Stripe PI: {} (charge.refunded)", id)
            );
        });
    }

    private Optional<String> getPaymentIntentId(Event event) {
        return event.getDataObjectDeserializer()
                .getObject()
                .map(obj -> {
                    if (obj instanceof PaymentIntent pi) {
                        return pi.getId();
                    }
                    log.warn("Expected PaymentIntent in event {} but got: {}", event.getId(), obj.getClass().getSimpleName());
                    return null;
                });
    }
}
