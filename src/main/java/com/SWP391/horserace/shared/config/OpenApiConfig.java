package com.SWP391.horserace.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration.
 *
 * <p>UI:   http://localhost:8080/swagger-ui.html
 * <p>JSON: http://localhost:8080/v3/api-docs
 *
 * <p>Registers an HTTP "bearer" (JWT) scheme so the <b>Authorize</b> button in Swagger UI
 * lets you paste an access token and call the protected endpoints. Get a token from
 * {@code POST /api/v1/auth/login}, click <b>Authorize</b>, paste it (no "Bearer " prefix needed).
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI horseRaceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("HorseRace API")
                        .description("Horse Racing Tournament Management System — REST API")
                        .version("v1")
                        .contact(new Contact().name("SWP391 Team")))
                // Apply the bearer requirement globally; public /auth/** endpoints simply ignore it.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
