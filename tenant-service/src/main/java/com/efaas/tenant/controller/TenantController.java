package com.efaas.tenant.controller;

import com.efaas.common.dto.ApiKeyDTO;
import com.efaas.common.dto.TenantDTO;
import com.efaas.tenant.domain.Tenant;
import com.efaas.tenant.service.ApiKeyService;
import com.efaas.tenant.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

/**
 * REST Controller for Tenant Management endpoints.
 * Handles CRUD operations and API key management.
 */
@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Tenant Management", description = "APIs for managing tenants and API keys")
public class TenantController {

    private final TenantService tenantService;
    private final ApiKeyService apiKeyService;

    @PostMapping
    @Operation(summary = "Create a new tenant")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Tenant created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid input")
    })
    public ResponseEntity<TenantDTO> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        log.info("Creating tenant: name={}", request.getName());
        TenantDTO tenant = tenantService.createTenant(
            request.getName(),
            request.getEmail(),
            Tenant.PlanTier.valueOf(request.getPlan().toUpperCase())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(tenant);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a tenant by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tenant found"),
        @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    public ResponseEntity<TenantDTO> getTenant(@Parameter(description = "Tenant ID", required = true) @PathVariable("id") UUID id) {
        log.debug("Fetching tenant: id={}", id);
        TenantDTO tenant = tenantService.getTenant(id);
        return ResponseEntity.ok(tenant);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a tenant")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tenant updated"),
        @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    public ResponseEntity<TenantDTO> updateTenant(@Parameter(description = "Tenant ID", required = true) @PathVariable("id") UUID id, @Valid @RequestBody UpdateTenantRequest request) {
        log.info("Updating tenant: id={}", id);
        TenantDTO tenant = tenantService.updateTenant(
            id,
            request.getName(),
            request.getEmail(),
            request.getPlan() != null ? Tenant.PlanTier.valueOf(request.getPlan().toUpperCase()) : null
        );
        return ResponseEntity.ok(tenant);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a tenant")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Tenant deleted"),
        @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    public ResponseEntity<Void> deleteTenant(@Parameter(description = "Tenant ID", required = true) @PathVariable("id") UUID id) {
        log.info("Deleting tenant: id={}", id);
        tenantService.deleteTenant(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/api-keys")
    @Operation(summary = "Generate a new API key for a tenant")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "API key generated (store securely - won't be shown again)"),
        @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    public ResponseEntity<ApiKeyDTO> generateApiKey(@Parameter(description = "Tenant ID", required = true) @PathVariable("id") UUID id) {
        log.info("Generating API key for tenant: tenantId={}", id);
        ApiKeyDTO apiKey = apiKeyService.generateApiKey(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(apiKey);
    }

    @GetMapping("/{id}/api-keys")
    @Operation(summary = "List all active API keys for a tenant")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of active API keys"),
        @ApiResponse(responseCode = "404", description = "Tenant not found")
    })
    public ResponseEntity<List<ApiKeyDTO>> listApiKeys(@Parameter(description = "Tenant ID", required = true) @PathVariable("id") UUID id) {
        log.debug("Listing API keys for tenant: tenantId={}", id);
        // Verify tenant exists
        tenantService.getTenant(id);
        List<ApiKeyDTO> keys = apiKeyService.getActiveKeysForTenant(id);
        return ResponseEntity.ok(keys);
    }

    @DeleteMapping("/api-keys/{keyId}")
    @Operation(summary = "Revoke an API key")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "API key revoked"),
        @ApiResponse(responseCode = "404", description = "API key not found")
    })
    public ResponseEntity<Void> revokeApiKey(@Parameter(description = "API Key ID", required = true) @PathVariable("keyId") UUID keyId) {
        log.info("Revoking API key: keyId={}", keyId);
        apiKeyService.revokeApiKey(keyId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/health")
    @Operation(summary = "Health check endpoint", hidden = true)
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    // DTOs for request payloads

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CreateTenantRequest {
        @jakarta.validation.constraints.NotBlank(message = "Name is required")
        @jakarta.validation.constraints.Size(min = 3, max = 100)
        private String name;

        @jakarta.validation.constraints.NotBlank(message = "Email is required")
        @jakarta.validation.constraints.Email
        private String email;

        @jakarta.validation.constraints.NotBlank(message = "Plan is required")
        @jakarta.validation.constraints.Pattern(regexp = "FREE|BASIC|PRO")
        private String plan;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UpdateTenantRequest {
        @jakarta.validation.constraints.Size(min = 3, max = 100)
        private String name;

        @jakarta.validation.constraints.Email
        private String email;

        @jakarta.validation.constraints.Pattern(regexp = "FREE|BASIC|PRO")
        private String plan;
    }
}
