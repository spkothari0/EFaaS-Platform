package com.efaas.payment.dto.plaid;

import java.util.UUID;

public record AccountBalanceResponse(
        UUID accountId,
        String name,
        String mask,
        Double available,
        Double current,
        Double limit,
        String isoCurrencyCode
) {}
