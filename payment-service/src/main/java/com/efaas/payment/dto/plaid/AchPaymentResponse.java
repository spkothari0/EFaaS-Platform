package com.efaas.payment.dto.plaid;

import com.efaas.payment.entity.AchPayment;
import com.efaas.payment.entity.AchPaymentStatus;

import java.util.UUID;

public record AchPaymentResponse(
        UUID id,
        String plaidTransferId,
        Long amount,
        String currency,
        String description,
        AchPaymentStatus status
) {
    public static AchPaymentResponse from(AchPayment payment) {
        return new AchPaymentResponse(
                payment.getId(),
                payment.getPlaidTransferId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getDescription(),
                payment.getStatus()
        );
    }
}
