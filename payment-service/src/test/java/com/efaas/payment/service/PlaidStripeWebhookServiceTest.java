package com.efaas.payment.service;

import com.efaas.payment.entity.AchPayment;
import com.efaas.payment.entity.AchPaymentStatus;
import com.efaas.payment.entity.PlaidItem;
import com.efaas.payment.repository.AchPaymentRepository;
import com.efaas.payment.repository.PlaidItemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plaid.client.model.TransferEvent;
import com.plaid.client.model.TransferEventSyncResponse;
import com.plaid.client.model.TransferEventType;
import com.plaid.client.request.PlaidApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.Call;
import retrofit2.Response;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaidStripeWebhookServiceTest {

    @Mock PlaidApi plaidApi;
    @Mock PlaidItemRepository plaidItemRepository;
    @Mock AchPaymentRepository achPaymentRepository;

    // Real ObjectMapper — needed to actually parse the JSON webhook payloads
    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks PlaidWebhookService webhookService;

    @Test
    void processWebhook_itemError_marksItemAsError() {
        String itemId = "item-abc";
        PlaidItem item = PlaidItem.builder()
                .id(UUID.randomUUID()).plaidItemId(itemId).status("ACTIVE").build();
        when(plaidItemRepository.findByPlaidItemId(itemId)).thenReturn(Optional.of(item));

        webhookService.processWebhook("""
                {"webhook_type":"ITEM","webhook_code":"ERROR","item_id":"%s"}
                """.formatted(itemId));

        assertThat(item.getStatus()).isEqualTo("ERROR");
        verify(plaidItemRepository).save(item);
    }

    @Test
    void processWebhook_transferPosted_updatesAchPaymentStatus() throws Exception {
        String transferId = "transfer-xyz";
        AchPayment payment = AchPayment.builder()
                .id(UUID.randomUUID())
                .plaidTransferId(transferId)
                .status(AchPaymentStatus.PENDING)
                .build();
        when(achPaymentRepository.findByPlaidTransferId(transferId)).thenReturn(Optional.of(payment));

        TransferEvent event = new TransferEvent()
                .eventId(1)
                .transferId(transferId)
                .eventType(TransferEventType.POSTED);
        TransferEventSyncResponse syncBody = new TransferEventSyncResponse()
                .transferEvents(List.of(event));

        Call<TransferEventSyncResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.success(syncBody));
        when(plaidApi.transferEventSync(any())).thenReturn(call);

        webhookService.processWebhook(
                """
                {"webhook_type":"TRANSFER","webhook_code":"TRANSFER_EVENTS_PENDING"}
                """);

        assertThat(payment.getStatus()).isEqualTo(AchPaymentStatus.POSTED);
        verify(achPaymentRepository).save(payment);
    }

    @Test
    void processWebhook_unknownType_doesNothing() {
        webhookService.processWebhook("""
                {"webhook_type":"INVESTMENTS_TRANSACTIONS","webhook_code":"DEFAULT_UPDATE"}
                """);
        verifyNoInteractions(plaidItemRepository, achPaymentRepository);
    }

    @Test
    void processWebhook_malformedJson_doesNotThrow() {
        // Should log error and return gracefully — must not propagate exception
        webhookService.processWebhook("not-valid-json{{{");
        verifyNoInteractions(plaidItemRepository, achPaymentRepository);
    }
}
