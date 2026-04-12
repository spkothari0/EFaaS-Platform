package com.efaas.payment.controller;

import com.efaas.payment.service.StripeWebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Receives Stripe webhook events.
 *
 * IMPORTANT: This endpoint must receive the raw request body bytes for Stripe signature
 * verification to work. Do NOT let Spring parse it as JSON first.
 *
 * This endpoint is excluded from API key / JWT authentication since Stripe sends its
 * own signature in the Stripe-Signature header.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Hidden
public class StripeWebhookController {

    private final StripeWebhookService stripeWebhookService;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @PostMapping(value = "/webhooks/stripe", consumes = "application/json")
    public ResponseEntity<String> handleWebhook(
            @RequestHeader("Stripe-Signature") String sigHeader,
            @RequestBody String payload) {

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe webhook signature: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid signature");
        } catch (Exception e) {
            log.warn("Failed to parse webhook payload: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid payload");
        }

        try {
            stripeWebhookService.processEvent(event);
        } catch (Exception e) {
            // Return 500 so Stripe retries — do not swallow the error silently
            log.error("Error processing Stripe event {}: {}", event.getId(), e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Processing failed");
        }

        return ResponseEntity.ok("Received");
    }
}
