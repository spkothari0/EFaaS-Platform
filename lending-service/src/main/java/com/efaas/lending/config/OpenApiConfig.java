package com.efaas.lending.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Lending Service API",
        version = "v1",
        description = "Loan application, credit scoring, repayment scheduling and tracking",
        contact = @Contact(name = "EFaaS Platform", email = "platform@efaas.io")
    ),
    servers = {
        @Server(url = "http://localhost:8083", description = "Direct"),
        @Server(url = "http://localhost:8080", description = "Via Gateway")
    },
    security = {
        @SecurityRequirement(name = "apiKeyAuth"),
        @SecurityRequirement(name = "bearerAuth")
    }
)
@SecurityScheme(
    name = "apiKeyAuth",
    type = SecuritySchemeType.APIKEY,
    in = SecuritySchemeIn.HEADER,
    paramName = "X-Api-Key"
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    in = SecuritySchemeIn.HEADER
)
public class OpenApiConfig {
}
