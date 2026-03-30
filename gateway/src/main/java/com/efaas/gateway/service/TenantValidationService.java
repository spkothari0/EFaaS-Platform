package com.efaas.gateway.service;

import com.efaas.common.dto.ApiKeyValidationResponse;
import com.efaas.common.exception.InvalidApiKeyException;
import com.efaas.gateway.config.EfaasGatewayProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Validates API keys by calling the Tenant Service, with Redis caching to avoid
 * a round-trip to tenant-service on every request.
 *
 * Cache key: apikey:ctx:{sha256(apiKey)}  →  JSON(ApiKeyValidationResponse)
 * TTL: configured via efaas.gateway.api-key-cache-ttl-seconds (default 5 min)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TenantValidationService {

    private final WebClient.Builder webClientBuilder;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final EfaasGatewayProperties properties;

    private static final String CACHE_PREFIX = "apikey:ctx:";

    /**
     * Returns the tenant context for the given API key.
     * Checks Redis cache first; falls back to tenant-service HTTP call.
     */
    public Mono<ApiKeyValidationResponse> validateAndGetContext(String apiKey) {
        String cacheKey = CACHE_PREFIX + hashKey(apiKey);

        return redisTemplate.opsForValue().get(cacheKey)
            .flatMap(json -> {
                try {
                    return Mono.just(objectMapper.readValue(json, ApiKeyValidationResponse.class));
                } catch (Exception e) {
                    log.warn("Failed to deserialize cached API key context, re-validating", e);
                    return Mono.<ApiKeyValidationResponse>empty();
                }
            })
            .switchIfEmpty(
                callTenantService(apiKey)
                    .flatMap(ctx -> cacheContext(cacheKey, ctx).thenReturn(ctx))
            );
    }

    /**
     * Evicts the cached context for an API key (call this on key revocation).
     */
    public Mono<Boolean> evictCache(String apiKey) {
        return redisTemplate.delete(CACHE_PREFIX + hashKey(apiKey)).map(count -> count > 0);
    }

    private Mono<ApiKeyValidationResponse>  callTenantService(String apiKey) {
        return webClientBuilder.build()
            .post()
            .uri(properties.getTenantServiceUrl() + "/internal/api-keys/validate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("apiKey", apiKey))
            .retrieve()
            .onStatus(
                status -> status.value() == 400 || status.value() == 401,
                response -> Mono.error(InvalidApiKeyException.notFound())
            )
            .bodyToMono(ApiKeyValidationResponse.class)
            .doOnNext(ctx -> log.debug("Tenant-service validated key: tenantId={}, plan={}",
                ctx.getTenantId(), ctx.getPlanTier()));
    }

    private Mono<Boolean> cacheContext(String cacheKey, ApiKeyValidationResponse ctx) {
        try {
            String json = objectMapper.writeValueAsString(ctx);
            return redisTemplate.opsForValue()
                .set(cacheKey, json, Duration.ofSeconds(properties.getApiKeyCacheTtlSeconds()));
        } catch (Exception e) {
            log.warn("Failed to cache API key context", e);
            return Mono.just(false);
        }
    }

    private String hashKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
