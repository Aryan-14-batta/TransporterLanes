package com.example.transporterlanes.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** API-level metadata for the generated OpenAPI document and Swagger UI. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI transporterLanesOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Transporter Assignment on Lanes API")
                .version("v1")
                .description("""
                        Assigns transporters to freight lanes at minimum total cost.

                        1. `POST /api/v1/transporters/input` stores lanes, transporters and their quotes, \
                        replacing any previously stored dataset.
                        2. `POST /api/v1/transporters/assignment` returns the cheapest assignment that covers \
                        every lane with **at most** `maxTransporters` transporters.

                        Ties: equal cost → fewer transporters → lexicographically smaller sorted transporter ids; \
                        equal quotes on a lane → lower transporter id.

                        Quotes are whole rupees (integers, 1 to 1,000,000,000,000). All errors share one body: \
                        `{"status": "error", "message": ..., "errors": [{"field": ..., "message": ...}]}`."""));
    }
}
