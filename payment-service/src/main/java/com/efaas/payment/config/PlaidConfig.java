package com.efaas.payment.config;

import com.plaid.client.ApiClient;
import com.plaid.client.request.PlaidApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

@Slf4j
@Configuration
public class PlaidConfig {

    @Value("${plaid.client-id}")
    private String clientId;

    @Value("${plaid.secret}")
    private String secret;

    @Value("${plaid.env}")
    private String env;

    @Bean
    public PlaidApi plaidApi() {
        HashMap<String, String> apiKeys = new HashMap<>();
        apiKeys.put("clientId", clientId);
        apiKeys.put("secret", secret);
        apiKeys.put("plaidVersion", "2020-09-14");

        ApiClient apiClient = new ApiClient(apiKeys);

        String baseUrl = switch (env.toLowerCase()) {
            case "production" -> ApiClient.Production;
            default -> ApiClient.Sandbox;
        };
        apiClient.setPlaidAdapter(baseUrl);

        log.info("Plaid SDK initialized (env: {})", env);
        return apiClient.createService(PlaidApi.class);
    }
}
