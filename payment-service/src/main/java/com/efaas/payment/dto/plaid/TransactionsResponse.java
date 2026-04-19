package com.efaas.payment.dto.plaid;

import java.util.List;
import java.util.UUID;

public record TransactionsResponse(
        UUID accountId,
        List<TransactionDto> transactions,
        int totalTransactions
) {}
