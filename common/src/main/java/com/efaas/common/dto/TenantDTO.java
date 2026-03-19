package com.efaas.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Data Transfer Object for Tenant.
 * Used in API responses and internal communication between services.
 * Never expose JPA entities directly in API responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantDTO {
    private UUID id;

    @NotBlank(message = "Tenant name is required")
    @Size(min = 3, max = 100, message = "Tenant name must be between 3 and 100 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotNull(message = "Plan is required")
    private PlanTier plan;

    private boolean active;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    public enum PlanTier {
        FREE,      // 10 req/min, basic features
        BASIC,     // 100 req/min, standard features
        PRO        // 1000 req/min, all features
    }
}
