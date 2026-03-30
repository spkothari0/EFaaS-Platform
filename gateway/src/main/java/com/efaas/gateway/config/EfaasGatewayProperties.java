package com.efaas.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway-specific configuration properties.
 * Loaded from application.yml under the 'efaas.gateway' prefix.
 */
@Configuration
@ConfigurationProperties(prefix = "efaas.gateway")
@Data
public class EfaasGatewayProperties {

    /** Base URL of the tenant-service for internal API key validation. */
    private String tenantServiceUrl = "http://localhost:8081";

    /** How long (seconds) to cache a validated API key's context in Redis. */
    private long apiKeyCacheTtlSeconds = 300;
}
