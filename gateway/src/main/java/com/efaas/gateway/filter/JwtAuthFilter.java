package com.efaas.gateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * JWT Authentication Filter (Skeleton).
 * This filter validates JWT tokens in the Authorization header.
 * Full implementation will be added in Week 2 with token verification.
 *
 * For now, this is a skeleton that:
 * - Logs incoming requests
 * - Allows requests without Bearer token (will be enforced later)
 * - Passes through to downstream services
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilter implements GlobalFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Skip auth for health checks and public endpoints
        if (path.contains("/actuator/health") || path.contains("/swagger-ui") || path.contains("/api-docs")) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            // TODO: In Week 2, return 401 Unauthorized
            // For now, allow requests to pass through
        } else {
            String token = authHeader.substring(7);
            log.debug("JWT token received for path: {}", path);
            // TODO: In Week 2, validate JWT token signature and expiration
        }

        return chain.filter(exchange);
    }
}
