package com.efaas.payment.dto;

import com.efaas.payment.entity.Payment;
import com.efaas.payment.entity.PaymentStatus;

import java.time.ZonedDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID paymentId,
        String stripePaymentIntentId,
        String clientSecret,
        PaymentStatus status,
        Long amount,
        String currency,
        String description,
        ZonedDateTime createdAt
) {
    public static PaymentResponse from(Payment payment, String clientSecret) {
        return new PaymentResponse(
                payment.getId(),
                payment.getStripePaymentIntentId(),
                clientSecret,
                payment.getStatus(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getDescription(),
                payment.getCreatedAt()
        );
    }

    public static PaymentResponse from(Payment payment) {
        return from(payment, null);
    }
}
