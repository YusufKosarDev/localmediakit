package com.localmediakit.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata; served at /swagger-ui.html and /v3/api-docs. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI localMediaKitOpenApi() {
        return new OpenAPI().info(new Info()
                .title("LocalMediaKit API")
                .version("1.0")
                .description("""
                        Live media kit platform for content creators. Public pages are
                        edge-cached and served from immutable publish snapshots; the backend
                        handles auth, kit editing, stats/engagement, analytics ingestion,
                        billing (Stripe test mode) and custom-domain DNS verification.
                        Education / portfolio project.""")
                .license(new License().name("Education / portfolio")));
    }
}
