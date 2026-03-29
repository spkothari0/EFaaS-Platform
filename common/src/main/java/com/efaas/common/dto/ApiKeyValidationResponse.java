package com.efaas.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO for internal API key validation between Gateway and Tenant Service.
 * Contains the tenant context needed by the gateway to apply rate limiting and inject tenant headers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyValidationResponse {
    private UUID tenantId;
    private String planTier;
    private int rateLimitPerMinute;
}
