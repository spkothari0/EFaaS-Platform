package com.efaas.lending.engine;

import com.efaas.common.dto.FinancialProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Rule-based credit scoring engine.

 * Scoring factors (total 1000 points):
 *   DTI Ratio          300 pts — monthly debt vs estimated income
 *   Payment History    250 pts — transactionCount90Days as activity proxy
 *   Balance Stability  200 pts — available/current balance ratio
 *   Income Level       150 pts — estimatedMonthlyIncome absolute value
 *   Account Balance    100 pts — currentBalance absolute value

 * Thresholds:
 *   >= 700  → APPROVED
 *   500-699 → CONDITIONALLY_APPROVED
 *   < 500   → DENIED
 */
@Slf4j
@Component
public class CreditScoringEngine {

    public static final int APPROVE_THRESHOLD = 700;
    public static final int CONDITIONAL_THRESHOLD = 500;

    public int score(FinancialProfile profile, long requestedAmountCents, int termMonths) {
        int dtiScore= scoreDti(profile, requestedAmountCents, termMonths);
        int paymentScore = scorePaymentHistory(profile);
        int balanceStbScore = scoreBalanceStability(profile);
        int incomeScore = scoreIncomeLevel(profile);
        int balanceScore = scoreAccountBalance(profile);

        int capped = Math.max(0, Math.min(1000, dtiScore + paymentScore + balanceStbScore + incomeScore + balanceScore ));
        log.debug("Credit score computed: {} (DTI={}, history={}, stability={}, income={}, balance={})",
                capped,
                dtiScore,
                paymentScore,
                balanceStbScore,
                incomeScore,
                balanceScore);
        return capped;
    }

    // 300 pts — DTI: estimated monthly payment vs monthly income
    private int scoreDti(FinancialProfile profile, long requestedAmountCents, int termMonths) {
        if (profile.estimatedMonthlyIncome() == null || profile.estimatedMonthlyIncome() <= 0) return 0;
        double monthlyPayment = requestedAmountCents / 100.0 / termMonths;
        double monthlyIncome = profile.estimatedMonthlyIncome();
        double dti = monthlyPayment / monthlyIncome;
        if (dti < 0.20) return 300;
        if (dti < 0.36) return 250;
        if (dti < 0.50) return 150;
        return 0;
    }

    // 250 pts — payment history proxy: transaction activity in last 90 days
    private int scorePaymentHistory(FinancialProfile profile) {
        int count = profile.transactionCount90Days();
        if (count >= 30) return 250;
        if (count >= 15) return 150;
        if (count >= 5)  return 75;
        return 0;
    }

    // 200 pts — balance stability: available vs current ratio
    private int scoreBalanceStability(FinancialProfile profile) {
        Double current = profile.currentBalance();
        Double available = profile.availableBalance();
        if (current == null || current <= 0 || available == null) return 0;
        double ratio = available / current;
        if (ratio >= 0.80) return 200;
        if (ratio >= 0.50) return 100;
        if (ratio >= 0.20) return 50;
        return 0;
    }

    // 150 pts — income level
    private int scoreIncomeLevel(FinancialProfile profile) {
        if (profile.estimatedMonthlyIncome() == null) return 0;
        double income = profile.estimatedMonthlyIncome();
        if (income >= 10000) return 150;
        if (income >= 5000)  return 100;
        if (income >= 2500)  return 50;
        return 0;
    }

    // 100 pts — current account balance (in dollars)
    private int scoreAccountBalance(FinancialProfile profile) {
        if (profile.currentBalance() == null) return 0;
        double balance = profile.currentBalance();
        if (balance >= 10000) return 100;
        if (balance >= 5000)  return 75;
        if (balance >= 1000)  return 50;
        return 0;
    }
}
