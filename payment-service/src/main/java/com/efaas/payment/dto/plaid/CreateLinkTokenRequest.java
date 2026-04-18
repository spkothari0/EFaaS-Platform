package com.efaas.payment.dto.plaid;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateLinkTokenRequest(

        @NotBlank(message = "User ID is required")
        @Size(max = 255, message = "User ID cannot exceed 255 characters")
        String userId
) {}
