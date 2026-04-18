package com.efaas.payment.dto.plaid;

public record LinkTokenResponse(
        String linkToken,
        String expiration
) {}
