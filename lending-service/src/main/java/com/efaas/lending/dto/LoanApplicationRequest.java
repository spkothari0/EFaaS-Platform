package com.efaas.lending.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;

import java.util.UUID;

public record LoanApplicationRequest(
        @NotNull UUID plaidAccountId,
        @NotNull @Min(10000) long requestedAmountCents,
        @NotNull @Min(1) @Max(360) int termMonths,
        @NotBlank String purpose,
        @NotBlank String applicantUserId
) {}
