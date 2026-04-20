package com.efaas.lending.dto;

import java.time.LocalDate;

public record InstallmentDto(
        int installmentNumber,
        LocalDate dueDate,
        long principalCents,
        long interestCents,
        long totalCents,
        String status
) {}
