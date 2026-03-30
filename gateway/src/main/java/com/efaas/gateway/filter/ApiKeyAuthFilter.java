package com.efaas.gateway.filter;

import com.efaas.common.dto.ApiKeyValidationResponse;
import com.efaas.common.exception.InvalidApiKeyException;
import com.efaas.gateway.service.TenantValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Global gateway filter that:
 *   1. Extracts the API key from the request (X-Api-Key or Authorization: Bearer).
 *   2. Validates the key via TenantValidationService (with Redis caching).
 *   3. Applies a sliding-window rate limit per API key using Redis sorted sets.
 *   4. Injects X-Tenant-Id and X-Plan-Tier headers for downstream services.
 *
 * Returns:
 *   401 – missing or invalid API key
 *   429 – rate limit exceeded (includes X-RateLimit-* headers)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ApiKeyAuthFilter implements GlobalFilter, Ordered {

    private final TenantValidationService tenantValidationService;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    private static final List<String> SKIP_PREFIXES = List.of(
        "/actuator/", "/swagger-ui", "/v3/api-docs", "/webjars"
    );

    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:";
    private static final long WINDOW_MS = 60_000L;

    @Override
    public int getOrder() {
        return -1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (SKIP_PREFIXES.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        String apiKey = extractApiKey(exchange.getRequest());
        if (apiKey == null) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED,
                "Missing API key. Provide via 'X-Api-Key' header or 'Authorization: Bearer <key>'.");
        }

        return tenantValidationService.validateAndGetContext(apiKey)
            .flatMap(ctx -> applyRateLimit(exchange, apiKey, ctx)
                .flatMap(withinLimit -> {
                    if (!withinLimit) {
                        exchange.getResponse().getHeaders().set("Retry-After", "60");
                        return writeError(exchange, HttpStatus.TOO_MANY_REQUESTS,
                            "Rate limit exceeded. Your plan allows " + ctx.getRateLimitPerMinute()
                                + " requests/minute. Retry after 60 seconds.");
                    }

                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                        .header("X-Tenant-Id", ctx.getTenantId().toString())
                        .header("X-Plan-Tier", ctx.getPlanTier())
                        .build();

                    log.debug("Request authorized: tenantId={}, plan={}, path={}",
                        ctx.getTenantId(), ctx.getPlanTier(), path);

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                })
            )
            .onErrorResume(InvalidApiKeyException.class, e ->
                writeError(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired API key."));
    }

    /**
     * Sliding window rate limiter using Redis sorted sets.
     * Key: rate_limit:{sha256(apiKey)}
     * Score: request timestamp (millis)
     * Value: unique request UUID (prevents duplicate scores)
     */
    private Mono<Boolean> applyRateLimit(ServerWebExchange exchange, String apiKey,
                                          ApiKeyValidationResponse ctx) {
        String rateLimitKey = RATE_LIMIT_KEY_PREFIX + hashKey(apiKey);
        int limit = ctx.getRateLimitPerMinute();
        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_MS;

        return redisTemplate.opsForZSet()
            .removeRangeByScore(rateLimitKey, Range.closed(0.0, (double) windowStart))
            .then(redisTemplate.opsForZSet().count(rateLimitKey, Range.closed((double)windowStart, (double)now)))
            .flatMap(currentCount -> {
                long remaining = Math.max(0L, limit - currentCount);
                exchange.getResponse().getHeaders().set("X-RateLimit-Limit", String.valueOf(limit));
                exchange.getResponse().getHeaders().set("X-RateLimit-Remaining", String.valueOf(remaining));

                if (currentCount >= limit) {
                    return Mono.just(false);
                }

                return redisTemplate.opsForZSet()
                    .add(rateLimitKey, UUID.randomUUID().toString(), now)
                    .then(redisTemplate.expire(rateLimitKey, Duration.ofMinutes(2)))
                    .thenReturn(true);
            });
    }

    private String extractApiKey(ServerHttpRequest request) {
        String xApiKey = request.getHeaders().getFirst("X-Api-Key");
        if (xApiKey != null && !xApiKey.isBlank()) {
            return xApiKey;
        }
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = """
            {"status":%d,"error":"%s","message":"%s"}
            """.formatted(status.value(), status.getReasonPhrase(), message).strip();
        DataBuffer buffer = exchange.getResponse().bufferFactory()
            .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
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
