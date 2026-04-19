package com.efaas.payment.controller;

import com.efaas.payment.service.PlaidWebhookService;
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
 *
 * To test in sandbox:
 *   1. Go to https://dashboard.plaid.com/transfer, find your transfer, click "Next Event"
 *      to advance its status (pending → posted).
 *   2. POST the following to this endpoint to trigger the sync:
 *      POST http://localhost:8082/webhooks/plaid
 *      { "webhook_type": "TRANSFER", "webhook_code": "TRANSFER_EVENTS_PENDING" }
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
