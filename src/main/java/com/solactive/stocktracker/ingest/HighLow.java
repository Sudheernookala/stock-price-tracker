package com.solactive.stocktracker.ingest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Immutable high/low result of one symbol on one trading day.
 *
 * @param stockSymbol   ticker symbol
 * @param tradingDate   trading day
 * @param highPrice     highest price
 * @param highTimestamp earliest instant of the highest price
 * @param lowPrice      lowest price
 * @param lowTimestamp  earliest instant of the lowest price
 */
public record HighLow(
        String stockSymbol,
        LocalDate tradingDate,
        BigDecimal highPrice,
        Instant highTimestamp,
        BigDecimal lowPrice,
        Instant lowTimestamp) {

    /**
     * Starts a high/low from a single tick (it is both the high and the low).
     *
     * @param tick first tick seen for the symbol/day
     * @return the initial high/low
     */
    public static HighLow of(PriceTick tick) {
        return new HighLow(tick.stockSymbol(), tick.tradingDate(),
                tick.price(), tick.timestamp(), tick.price(), tick.timestamp());
    }

    /**
     * Combines two high/lows of the same symbol and trading day, e.g. the running result and the
     * result of a newly imported file.
     *
     * <p>Ties are broken by keeping the <em>earliest</em> timestamp. Because the rule depends only
     * on (price, timestamp) and not on arrival order, the result is the same however the input is
     * ordered or split across files.</p>
     *
     * @param other high/low of the same symbol and trading day
     * @return the combined high/low (this instance when nothing changes)
     */
    public HighLow merge(HighLow other) {
        boolean newHigh = isBetter(other.highPrice, other.highTimestamp, highPrice, highTimestamp, 1);
        boolean newLow = isBetter(other.lowPrice, other.lowTimestamp, lowPrice, lowTimestamp, -1);
        if (!newHigh && !newLow) {
            return this;
        }
        return new HighLow(stockSymbol, tradingDate,
                newHigh ? other.highPrice : highPrice, newHigh ? other.highTimestamp : highTimestamp,
                newLow ? other.lowPrice : lowPrice, newLow ? other.lowTimestamp : lowTimestamp);
    }

    /**
     * Decides whether a candidate price replaces the current one.
     *
     * @param direction {@code 1} when a higher price wins, {@code -1} when a lower price wins
     */
    private static boolean isBetter(BigDecimal candidate, Instant candidateTime,
                                    BigDecimal current, Instant currentTime, int direction) {
        int cmp = candidate.compareTo(current) * direction;
        return cmp > 0 || (cmp == 0 && candidateTime.isBefore(currentTime));
    }
}
