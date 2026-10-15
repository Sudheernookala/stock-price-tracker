package com.solactive.stocktracker.ingest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A validated, normalised price update ready to be aggregated. Exchange and volume are validated
 * but not kept, because the high/low does not use them.
 *
 * @param stockSymbol upper-case ticker symbol
 * @param price       price in USD with scale 6
 * @param timestamp   instant of the update, truncated to microseconds
 * @param tradingDate exchange-local trading day of {@code timestamp}
 */
public record PriceTick(
        String stockSymbol,
        BigDecimal price,
        Instant timestamp,
        LocalDate tradingDate) {

    /**
     * Guards the mandatory components.
     */
    public PriceTick {
        Objects.requireNonNull(stockSymbol, "stockSymbol");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(tradingDate, "tradingDate");
    }
}
