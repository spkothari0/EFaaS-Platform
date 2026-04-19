package com.efaas.payment.dto.plaid;

import java.time.LocalDate;
import java.util.List;

public record TransactionDto(
        String transactionId,
        String name,
        String merchantName,
        Double amount,
        LocalDate date,
        List<String> category,
        String isoCurrencyCode,
        boolean pending
) {}
