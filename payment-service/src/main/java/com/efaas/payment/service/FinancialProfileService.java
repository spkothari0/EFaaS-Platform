package com.efaas.payment.service;

import com.efaas.common.dto.FinancialProfile;
import com.efaas.payment.dto.plaid.TransactionDto;
import com.efaas.payment.dto.plaid.TransactionsResponse;
import com.efaas.payment.dto.plaid.AccountBalanceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Aggregates Plaid balance and transaction data into a FinancialProfile DTO
 * for consumption by the Lending service credit scoring engine.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialProfileService {

    private final PlaidService plaidService;

    public FinancialProfile buildProfile(UUID tenantId, UUID accountId) {
        AccountBalanceResponse balance = plaidService.getAccountBalance(tenantId, accountId);
        TransactionsResponse txResponse = plaidService.getAccountTransactions(tenantId, accountId);

        double estimatedMonthlyIncome = computeMonthlyIncome(txResponse.transactions());
        double averageMonthlyBalance = balance.current() != null ? balance.current() : 0.0;

        log.info("Built FinancialProfile for account {} tenant {}: income={}, balance={}",
                accountId, tenantId, estimatedMonthlyIncome, averageMonthlyBalance);

        return new FinancialProfile(
                tenantId,
                accountId,
                balance.current(),
                balance.available(),
                averageMonthlyBalance,
                estimatedMonthlyIncome,
                txResponse.totalTransactions(),
                LocalDate.now()
        );
    }

    // In Plaid: negative amount = money IN (income/credit). Sum abs values over 90 days ÷ 3.
    private double computeMonthlyIncome(List<TransactionDto> transactions) {
        double total = transactions.stream()
                .filter(t -> t.amount() != null && t.amount() < 0)
                .mapToDouble(t -> Math.abs(t.amount()))
                .sum();
        return total / 3.0;
    }
}
