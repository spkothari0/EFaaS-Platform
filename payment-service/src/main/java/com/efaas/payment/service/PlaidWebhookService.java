package com.efaas.payment.service;

import com.efaas.payment.entity.AchPaymentStatus;
import com.efaas.payment.repository.AchPaymentRepository;
import com.efaas.payment.repository.PlaidItemRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plaid.client.model.TransferEventSyncRequest;
import com.plaid.client.model.TransferEventSyncResponse;
import com.plaid.client.request.PlaidApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import retrofit2.Response;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaidWebhookService {

    private final PlaidApi plaidApi;
    private final PlaidItemRepository plaidItemRepository;
    private final AchPaymentRepository achPaymentRepository;
    private final ObjectMapper objectMapper;

    /**
     * afterId tracks the last processed Plaid transfer event.
     * TODO: persist this value in the database for production use.
     */
    private volatile int lastTransferEventId = 0;

    @Transactional
    public void processWebhook(String body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            log.error("Failed to parse Plaid webhook body: {}", e.getMessage());
            return;
        }

        String webhookType = root.path("webhook_type").asText();
        String webhookCode = root.path("webhook_code").asText();

        log.info("Received Plaid webhook: type={}, code={}", webhookType, webhookCode);

        switch (webhookType) {
            case "ITEM" -> handleItemWebhook(webhookCode, root);
            case "TRANSFER" -> handleTransferWebhook(webhookCode);
            default -> log.debug("Unhandled Plaid webhook type: {}", webhookType);
        }
    }

    // ─── ITEM webhooks ───────────────────────────────────────────────────────────

    private void handleItemWebhook(String code, JsonNode root) {
        switch (code) {
            case "ERROR" -> {
                String itemId = root.path("item_id").asText();
                plaidItemRepository.findByPlaidItemId(itemId).ifPresentOrElse(
                        item -> {
                            item.setStatus("ERROR");
                            plaidItemRepository.save(item);
                            log.warn("Plaid item {} marked ERROR (webhook)", itemId);
                        },
                        () -> log.warn("ITEM/ERROR webhook: no item found for plaidItemId={}", itemId)
                );
            }
            case "PENDING_EXPIRATION" -> {
                String itemId = root.path("item_id").asText();
                log.warn("Plaid item {} access token is pending expiration — user must re-link", itemId);
            }
            default -> log.debug("Unhandled ITEM webhook code: {}", code);
        }
    }

    // ─── TRANSFER webhooks ───────────────────────────────────────────────────────

    /**
     * Plaid sends TRANSFER/TRANSFER_EVENTS_PENDING when new transfer events are ready.
     * We use transferEventSync to pull all new events since the last sync.
     */
    private void handleTransferWebhook(String code) {
        if (!"TRANSFER_EVENTS_PENDING".equals(code)) {
            log.debug("Unhandled TRANSFER webhook code: {}", code);
            return;
        }

        try {
            TransferEventSyncRequest syncRequest = new TransferEventSyncRequest()
                    .afterId(lastTransferEventId);

            Response<TransferEventSyncResponse> response =
                    plaidApi.transferEventSync(syncRequest).execute();

            if (!response.isSuccessful() || response.body() == null) {
                log.error("transferEventSync failed (HTTP {})", response.code());
                return;
            }

            var events = response.body().getTransferEvents();
            if (events == null || events.isEmpty()) {
                return;
            }

            for (var event : events) {
                processTransferEvent(event.getTransferId(), event.getEventType().getValue());
                // Advance cursor so we don't reprocess events on subsequent webhooks
                if (event.getEventId() > lastTransferEventId) {
                    lastTransferEventId = event.getEventId();
                }
            }

            log.info("Processed {} Plaid transfer events (new cursor: {})",
                    events.size(), lastTransferEventId);

        } catch (IOException e) {
            log.error("Network error calling transferEventSync: {}", e.getMessage());
        }
    }

    private void processTransferEvent(String plaidTransferId, String eventType) {
        AchPaymentStatus newStatus = switch (eventType) {
            case "posted" -> AchPaymentStatus.POSTED;
            case "failed" -> AchPaymentStatus.FAILED;
            case "cancelled" -> AchPaymentStatus.CANCELLED;
            default -> null;
        };

        if (newStatus == null) {
            log.debug("Skipping non-terminal transfer event type: {}", eventType);
            return;
        }

        achPaymentRepository.findByPlaidTransferId(plaidTransferId).ifPresentOrElse(
                payment -> {
                    if (payment.getStatus() == AchPaymentStatus.PENDING) {
                        payment.setStatus(newStatus);
                        achPaymentRepository.save(payment);
                        log.info("ACH payment {} updated to {} via Plaid event", payment.getId(), newStatus);
                    }
                },
                () -> log.warn("Transfer event for unknown plaidTransferId={}", plaidTransferId)
        );
    }
}
