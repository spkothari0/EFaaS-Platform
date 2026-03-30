package com.efaas.gateway.filter;

import com.efaas.common.dto.ApiKeyValidationResponse;
import com.efaas.gateway.service.TenantValidationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration test for {@link ApiKeyAuthFilter} sliding-window rate limiting.
 * Uses a real Redis instance via Testcontainers.
 */
@Testcontainers
class RateLimitIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private ReactiveRedisTemplate<String, String> redisTemplate;
    private ApiKeyAuthFilter filter;
    private TenantValidationService mockValidationService;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
            redis.getHost(), redis.getMappedPort(6379));
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new ReactiveRedisTemplate<>(connectionFactory, RedisSerializationContext.string());

        mockValidationService = mock(TenantValidationService.class);
        filter = new ApiKeyAuthFilter(mockValidationService, redisTemplate);
    }

    @AfterEach
    void tearDown() {
        // Flush Redis between tests to avoid cross-test contamination
        redisTemplate.execute(connection -> connection.serverCommands().flushAll()).blockLast();
        connectionFactory.destroy();
    }

    @Test
    void shouldAllowRequestsWithinRateLimit() {
        String apiKey = "efaas_live_test_key_001";
        mockValidationResponse(apiKey, 5);

        for (int i = 0; i < 5; i++) {
            MockServerWebExchange exchange = exchangeWithKey(apiKey);
            filter.filter(exchange, chain -> {
                exchange.getResponse().setStatusCode(HttpStatus.OK);
                return Mono.empty();
            }).block();

            assertThat(exchange.getResponse().getStatusCode())
                .as("Request %d should be allowed", i + 1)
                .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    @Test
    void shouldReturn429WhenRateLimitExceeded() {
        String apiKey = "efaas_live_test_key_002";
        mockValidationResponse(apiKey, 3);

        // Exhaust the limit
        for (int i = 0; i < 3; i++) {
            MockServerWebExchange exchange = exchangeWithKey(apiKey);
            filter.filter(exchange, chain -> {
                exchange.getResponse().setStatusCode(HttpStatus.OK);
                return Mono.empty();
            }).block();
        }

        // Next request should be rejected
        MockServerWebExchange blocked = exchangeWithKey(apiKey);
        filter.filter(blocked, chain -> Mono.empty()).block();

        assertThat(blocked.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("60");
    }

    @Test
    void shouldReturn401WhenApiKeyIsMissing() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/tenants").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldEnforceRateLimitsPerKeyIndependently() {
        String keyA = "efaas_live_key_a";
        String keyB = "efaas_live_key_b";
        mockValidationResponse(keyA, 2);
        mockValidationResponse(keyB, 2);

        // Exhaust key A
        for (int i = 0; i < 2; i++) {
            filter.filter(exchangeWithKey(keyA), chain -> Mono.empty()).block();
        }

        // Key B should still be allowed
        MockServerWebExchange exchangeB = exchangeWithKey(keyB);
        filter.filter(exchangeB, chain -> {
            exchangeB.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        }).block();

        assertThat(exchangeB.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // Key A should be blocked
        MockServerWebExchange exchangeA = exchangeWithKey(keyA);
        filter.filter(exchangeA, chain -> Mono.empty()).block();
        assertThat(exchangeA.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void mockValidationResponse(String apiKey, int rateLimitPerMinute) {
        ApiKeyValidationResponse ctx = ApiKeyValidationResponse.builder()
            .tenantId(UUID.randomUUID())
            .planTier("FREE")
            .rateLimitPerMinute(rateLimitPerMinute)
            .build();
        when(mockValidationService.validateAndGetContext(apiKey)).thenReturn(Mono.just(ctx));
    }

    private MockServerWebExchange exchangeWithKey(String apiKey) {
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/tenants")
            .header("X-Api-Key", apiKey)
            .build();
        return MockServerWebExchange.from(request);
    }
}
