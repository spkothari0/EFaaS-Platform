package com.efaas.payment.controller;

import com.efaas.payment.service.PlaidWebhookService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Receives Plaid webhook events at /webhooks/plaid.
 *
 * This endpoint is excluded from API key authentication — Plaid does not send
 * an API key. In production, verify webhook authenticity via Plaid's JWT-based
 * webhook verification (plaid-verification-header). Skipped here for simplicity.
 *
 * Call this endpoint directly on port 8082 (not via the gateway) when testing locally.
 * Use the Plaid sandbox fire_webhook endpoint to simulate events.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class PlaidWebhookController {

    private final PlaidWebhookService webhookService;

    @PostMapping(value = "/webhooks/plaid", consumes = "application/json")
    public ResponseEntity<String> handleWebhook(@RequestBody String payload) {
        log.debug("Received Plaid webhook payload: {}", payload);
        try {
            webhookService.processWebhook(payload);
        } catch (Exception e) {
            // Return 500 so Plaid retries the webhook
            log.error("Error processing Plaid webhook: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Processing failed");
        }
        return ResponseEntity.ok("Received");
    }
}
