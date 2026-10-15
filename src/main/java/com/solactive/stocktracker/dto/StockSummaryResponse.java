package com.solactive.stocktracker.dto;

import com.solactive.stocktracker.ingest.HighLow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Daily high/low summary of one stock symbol, in the exact shape required by the specification.
 *
 * @param stockSymbol   ticker symbol
 * @param highPrice     highest observed price in USD (6 decimal places)
 * @param highTimestamp instant at which the high was first observed
 * @param lowPrice      lowest observed price in USD (6 decimal places)
 * @param lowTimestamp  instant at which the low was first observed
 */
@Schema(description = "Daily high/low summary for one stock symbol")
public record StockSummaryResponse(
        @Schema(example = "MSFT") String stockSymbol,
        @Schema(example = "420.000001") BigDecimal highPrice,
        @Schema(example = "2026-09-16T12:33:23.902691Z") Instant highTimestamp,
        @Schema(example = "380.010004") BigDecimal lowPrice,
        @Schema(example = "2026-09-16T12:50:42.902691Z") Instant lowTimestamp) {

    /**
     * Maps an internal high/low result to the API representation.
     *
     * @param highLow internal result
     * @return API representation
     */
    public static StockSummaryResponse from(HighLow highLow) {
        return new StockSummaryResponse(highLow.stockSymbol(), highLow.highPrice(), highLow.highTimestamp(),
                highLow.lowPrice(), highLow.lowTimestamp());
    }
}
