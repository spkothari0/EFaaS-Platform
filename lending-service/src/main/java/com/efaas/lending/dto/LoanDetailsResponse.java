package com.efaas.lending.dto;

import java.time.ZonedDateTime;
import java.util.UUID;

public record LoanDetailsResponse(
        UUID id,
        UUID tenantId,
        String applicantUserId,
        long principalAmountCents,
        double annualInterestRate,
        int termMonths,
        String status,
        int creditScore,
        Long monthlyPaymentCents,
        String purpose,
        String decisionReason,
        ZonedDateTime createdAt
) {}
