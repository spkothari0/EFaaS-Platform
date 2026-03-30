package com.efaas.tenant.controller;

import com.efaas.common.dto.ApiKeyValidationResponse;
import com.efaas.tenant.service.ApiKeyService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal endpoints consumed only by the API Gateway.
 * These paths are NOT routed through the gateway (no /api/v1/* prefix),
 * so they are never exposed to external clients.
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
@Slf4j
@Hidden
public class InternalController {

    private final ApiKeyService apiKeyService;

    /**
     * Validates an API key and returns the tenant context needed by the gateway
     * for rate limiting and header injection.
     */
    @PostMapping("/api-keys/validate")
    public ResponseEntity<ApiKeyValidationResponse> validateApiKey(@RequestBody ValidateRequest request) {
        log.debug("Internal API key validation request");
        ApiKeyValidationResponse response = apiKeyService.validateForGateway(request.getApiKey());
        return ResponseEntity.ok(response);
    }

    @Data
    public static class ValidateRequest {
        @NotBlank
        private String apiKey;
    }
}
