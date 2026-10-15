package com.solactive.stocktracker.dto;

import com.solactive.stocktracker.ingest.HighLow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * High/low of one symbol on one trading day. Used by the history endpoint, which returns several
 * days and therefore needs the date in each entry. The specification's endpoint keeps using
 * {@link StockSummaryResponse}, so its required format is unchanged.
 *
 * @param tradingDate   trading day (exchange-local date)
 * @param stockSymbol   ticker symbol
 * @param highPrice     highest price in USD (6 decimal places)
 * @param highTimestamp instant at which the high was first observed
 * @param lowPrice      lowest price in USD (6 decimal places)
 * @param lowTimestamp  instant at which the low was first observed
 */
@Schema(description = "High/low of one symbol on one trading day")
public record DailySummaryResponse(
        @Schema(example = "2026-09-16") LocalDate tradingDate,
        @Schema(example = "AAPL") String stockSymbol,
        @Schema(example = "180.123456") BigDecimal highPrice,
        @Schema(example = "2026-09-16T14:39:42.983730Z") Instant highTimestamp,
        @Schema(example = "153.215726") BigDecimal lowPrice,
        @Schema(example = "2026-09-16T14:39:33.983730Z") Instant lowTimestamp) {

    /**
     * Maps an internal high/low result to the API representation.
     *
     * @param highLow internal result
     * @return API representation
     */
    public static DailySummaryResponse from(HighLow highLow) {
        return new DailySummaryResponse(highLow.tradingDate(), highLow.stockSymbol(), highLow.highPrice(),
                highLow.highTimestamp(), highLow.lowPrice(), highLow.lowTimestamp());
    }
}
