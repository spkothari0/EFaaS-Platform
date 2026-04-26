package com.efaas.lending.client;

import com.efaas.common.dto.FinancialProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * HTTP client for calling payment-service's internal financial profile endpoint.
 * Called during loan application to fetch Plaid-derived financial data for scoring.
 */
@Slf4j
@Component
public class FinancialProfileClient {

    private final RestClient restClient;

    public FinancialProfileClient(
            @Value("${payment-service.base-url}") String baseUrl,
            RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .build();
    }

    public FinancialProfile getFinancialProfile(UUID tenantId, UUID accountId) {
        log.debug("Fetching financial profile for account={} tenant={}", accountId, tenantId);
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/financial-profile/{accountId}")
                        .queryParam("tenantId", tenantId)
                        .build(accountId))
                .retrieve()
                .body(FinancialProfile.class);
    }
}
