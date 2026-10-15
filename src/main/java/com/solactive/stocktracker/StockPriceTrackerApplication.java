package com.solactive.stocktracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the Stock Price Tracker service.
 *
 * <p>The service reads intraday stock price CSV files (from a folder or uploaded),
 * stores the high and low price of every symbol per trading day in PostgreSQL together with the
 * processing status of every file, and exposes both through a REST API.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class StockPriceTrackerApplication {

    /**
     * Boots the Spring application context.
     *
     * @param args command-line arguments forwarded to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(StockPriceTrackerApplication.class, args);
    }
}
