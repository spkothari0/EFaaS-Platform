package com.efaas.payment.dto.plaid;

import jakarta.validation.constraints.NotBlank;

public record ExchangeTokenRequest(

        @NotBlank(message = "Public token is required")
        String publicToken
) {}
