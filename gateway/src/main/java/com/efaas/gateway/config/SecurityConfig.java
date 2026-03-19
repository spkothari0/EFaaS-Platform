package com.efaas.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Gateway security configuration.
 * Disables default form-login and permits Swagger UI, actuator health,
 * and API docs paths without authentication.
 * JWT enforcement will be added when the JwtAuthFilter is fully implemented.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                // Swagger UI and OpenAPI docs
                .pathMatchers(
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/webjars/**"
                ).permitAll()
                // Actuator health check
                .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                // All API routes — JWT validation handled by JwtAuthFilter
                .anyExchange().permitAll()
            )
            .build();
    }
}
