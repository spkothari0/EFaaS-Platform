package com.efaas.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Records processed Stripe webhook events for idempotency.
 * Before processing any event, we check this table. If stripeEventId already exists
 * we skip it — this handles Stripe's at-least-once delivery guarantee.
 */
@Entity
@Table(name = "webhook_events", indexes = {
        @Index(name = "idx_webhook_stripe_event_id", columnList = "stripe_event_id", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "stripe_event_id", nullable = false, unique = true, length = 100)
    private String stripeEventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private ZonedDateTime processedAt;
}
