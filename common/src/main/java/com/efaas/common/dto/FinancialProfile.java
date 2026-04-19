package com.efaas.common.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Aggregated financial snapshot derived from Plaid balance + transaction data.
 * Consumed by the Lending service for credit scoring.
 */
public record FinancialProfile(
        UUID tenantId,
        UUID accountId,
        Double currentBalance,
        Double availableBalance,
        Double averageMonthlyBalance,
        Double estimatedMonthlyIncome,
        int transactionCount90Days,
        LocalDate dataAsOf
) {}
