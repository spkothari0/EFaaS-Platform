package com.efaas.lending.service;

import com.efaas.lending.entity.Loan;
import com.efaas.lending.entity.RepaymentInstallment;
import com.efaas.lending.entity.RepaymentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates a standard amortization schedule for a loan.
 *
 * Formula: monthlyPayment = P * r * (1+r)^n / ((1+r)^n - 1)
 * Where P = principal (dollars), r = monthly rate, n = term months.
 *
 * All stored values are in cents (long) to avoid floating-point money issues.
 */
@Slf4j
@Service
public class AmortizationService {

    public List<RepaymentInstallment> generateSchedule(Loan loan) {
        double principal = loan.getPrincipalAmountCents() / 100.0;
        double annualRate = loan.getAnnualInterestRate();
        int termMonths = loan.getTermMonths();
        double monthlyRate = annualRate / 12.0 / 100.0;

        double monthlyPayment;
        if (monthlyRate == 0) {
            monthlyPayment = principal / termMonths;
        } else {
            double factor = Math.pow(1 + monthlyRate, termMonths);
            monthlyPayment = principal * monthlyRate * factor / (factor - 1);
        }

        long monthlyPaymentCents = Math.round(monthlyPayment * 100);
        loan.setMonthlyPaymentCents(monthlyPaymentCents);

        List<RepaymentInstallment> schedule = new ArrayList<>();
        double remainingBalance = principal;
        LocalDate dueDate = LocalDate.now().plusMonths(1);

        for (int i = 1; i <= termMonths; i++) {
            double interestAmount = remainingBalance * monthlyRate;
            double principalAmount = monthlyPayment - interestAmount;

            // Last installment absorbs rounding difference
            if (i == termMonths) {
                principalAmount = remainingBalance;
            }

            long interestCents = Math.round(interestAmount * 100);
            long principalCents = Math.round(principalAmount * 100);
            long totalCents = principalCents + interestCents;

            schedule.add(RepaymentInstallment.builder()
                    .loan(loan)
                    .installmentNumber(i)
                    .dueDate(dueDate)
                    .principalCents(principalCents)
                    .interestCents(interestCents)
                    .totalCents(totalCents)
                    .status(RepaymentStatus.PENDING)
                    .build());

            remainingBalance -= principalAmount;
            dueDate = dueDate.plusMonths(1);
        }

        log.debug("Generated {} installments for loan {}, monthly payment: {} cents",
                schedule.size(), loan.getId(), monthlyPaymentCents);
        return schedule;
    }
}
