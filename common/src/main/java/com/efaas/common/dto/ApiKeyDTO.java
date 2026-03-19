package com.efaas.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * API Key DTO. Note: The plaintext key is only returned on creation.
 * The maskedKey is shown in responses for security (e.g., "efaas_live_...2a5c9b").
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyDTO {
    private UUID id;
    private UUID tenantId;
    private String key;  // Plaintext - only returned on creation
    private String maskedKey;  // Masked for security
    private boolean active;
    private ZonedDateTime createdAt;
    private ZonedDateTime expiresAt;
}
