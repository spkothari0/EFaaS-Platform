package com.efaas.lending.dto;

import java.util.List;
import java.util.UUID;

public record RepaymentScheduleResponse(
        UUID loanId,
        long totalAmountCents,
        List<InstallmentDto> installments
) {}
