package com.famy.shared.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/** Contrato OpenAPI: {@code /v3/api-docs} y Swagger UI en {@code /swagger-ui.html}. */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Famy API", version = "v1",
                description = "API de Famy: organización familiar. Errores en application/problem+json (RFC 9457)."),
        security = @SecurityRequirement(name = "bearer"))
@SecurityScheme(name = "bearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
