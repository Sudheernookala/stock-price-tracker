package com.solactive.stocktracker.ingest;

import java.time.LocalDate;

/**
 * Identifies one high/low: a symbol on a trading day.
 *
 * @param stockSymbol normalised ticker symbol
 * @param tradingDate trading day
 */
public record SummaryKey(String stockSymbol, LocalDate tradingDate) {

    /**
     * Key of a tick.
     *
     * @param tick a validated tick
     * @return the key the tick belongs to
     */
    public static SummaryKey of(PriceTick tick) {
        return new SummaryKey(tick.stockSymbol(), tick.tradingDate());
    }
}
