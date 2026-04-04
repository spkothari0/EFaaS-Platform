package com.efaas.payment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePaymentRequest(

        @NotNull(message = "Amount is required")
        @Min(value = 50, message = "Amount must be at least 50 cents")
        Long amount,

        @NotBlank(message = "Currency is required")
        @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
        String currency,

        @Size(max = 500, message = "Description cannot exceed 500 characters")
        String description,

        @NotBlank(message = "Idempotency key is required")
        @Size(max = 255, message = "Idempotency key cannot exceed 255 characters")
        String idempotencyKey
) {
    // Default currency to "usd" if not provided — callers should always pass it explicitly
    public String currency() {
        return currency != null ? currency.toLowerCase() : "usd";
    }
}
