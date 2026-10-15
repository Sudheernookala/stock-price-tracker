package com.solactive.stocktracker.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI metadata. The UI is served at {@code /swagger-ui.html}.
 */
@Configuration
public class OpenApiConfig {

    /**
     * Describes the API shown in Swagger UI.
     *
     * @return OpenAPI document root
     */
    @Bean
    public OpenAPI stockTrackerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Stock Price Tracker API")
                .version("v1")
                .description("""
                        Imports intraday stock price CSV files and reports the daily high and low \
                        price (with the timestamp at which each occurred) for every stock symbol.""")
                .license(new License().name("Proprietary")));
    }
}
