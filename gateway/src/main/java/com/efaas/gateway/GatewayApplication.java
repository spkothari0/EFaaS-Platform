package com.efaas.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

/**
 * Spring Cloud Gateway application.
 * Routes all incoming API requests to appropriate microservices.
 */
@SpringBootApplication(scanBasePackages = {"com.efaas"})
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    /**
     * Define routes from gateway to downstream services.
     * Routes are configured here and also in application.yml for flexibility.
     */
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("tenant-service",
                r -> r.path("/api/v1/tenants/**")
                    .uri("http://tenant-service:8081"))
            // Future routes:
            // .route("payment-service", r -> r.path("/api/v1/payments/**").uri("http://payment-service:8082"))
            // .route("lending-service", r -> r.path("/api/v1/loans/**").uri("http://lending-service:8083"))
            // .route("investment-service", r -> r.path("/api/v1/orders/**").uri("http://investment-service:8084"))
            // .route("fraud-service", r -> r.path("/api/v1/risk/**").uri("http://fraud-service:8085"))
            .build();
    }
}
