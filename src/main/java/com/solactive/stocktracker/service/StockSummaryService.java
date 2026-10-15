package com.solactive.stocktracker.service;

import com.solactive.stocktracker.dto.DailySummaryResponse;
import com.solactive.stocktracker.dto.StockSummaryResponse;
import java.time.LocalDate;
import java.util.List;

/**
 * Read access to the daily high/low summaries.
 */
public interface StockSummaryService {

    /**
     * Returns the high/low of every symbol for a trading day.
     *
     * @param tradingDate trading day, or {@code null} for the most recent day with data for any
     *                    symbol (a symbol without data on that day is not listed)
     * @return summaries ordered by symbol; empty when there is no data
     */
    List<StockSummaryResponse> getDailySummaries(LocalDate tradingDate);

    /**
     * Returns the high/low of one symbol for a trading day.
     *
     * @param symbol      symbol in any case
     * @param tradingDate trading day, or {@code null} for the most recent day with data for
     *                    <em>this</em> symbol
     * @return the summary
     * @throws com.solactive.stocktracker.exception.ResourceNotFoundException when no data exists
     */
    StockSummaryResponse getSymbolSummary(String symbol, LocalDate tradingDate);

    /**
     * Returns the high/low of one symbol for every trading day with data.
     *
     * @param symbol symbol in any case
     * @return one entry per trading day, oldest first
     * @throws com.solactive.stocktracker.exception.ResourceNotFoundException when the symbol has no data
     */
    List<DailySummaryResponse> getSymbolHistory(String symbol);
}
