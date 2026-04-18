package com.efaas.payment.dto.plaid;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record InitiateAchPaymentRequest(

        @NotNull(message = "Account ID is required")
        UUID accountId,

        @NotNull(message = "Amount is required")
        @Min(value = 100, message = "Amount must be at least 100 cents ($1.00)")
        Long amount,

        @NotBlank(message = "Currency is required")
        @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
        String currency,

        @Size(max = 500, message = "Description cannot exceed 500 characters")
        String description,

        @NotBlank(message = "Account holder name is required for ACH transfer")
        @Size(max = 255)
        String accountHolderName,

        @NotBlank(message = "Idempotency key is required")
        @Size(max = 255, message = "Idempotency key cannot exceed 255 characters")
        String idempotencyKey
) {
    public String currency() {
        return currency != null ? currency.toLowerCase() : "usd";
    }
}
