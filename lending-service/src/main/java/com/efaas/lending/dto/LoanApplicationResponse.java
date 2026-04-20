package com.efaas.lending.dto;

import java.util.UUID;

public record LoanApplicationResponse(
        UUID loanId,
        String status,
        int creditScore,
        String decisionReason,
        Long monthlyPaymentCents
) {}
