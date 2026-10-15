package com.solactive.stocktracker.repository;

import com.solactive.stocktracker.ingest.HighLow;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Storage of daily high/low results.
 *
 * <p>Services depend on this interface, not on an implementation (dependency inversion). The
 * current implementation stores results in PostgreSQL; another store could replace it without any
 * change to services or controllers.</p>
 */
public interface SummaryRepository {

    /**
     * Merges new results into the stored ones: the stored high/low of each symbol and day keeps
     * the higher high and the lower low (earliest timestamp on ties). Must be safe to call from
     * several threads at once.
     *
     * @param highLows results of one import
     */
    void mergeAll(Collection<HighLow> highLows);

    /**
     * Finds the high/low of every symbol for one day.
     *
     * @param tradingDate trading day
     * @return every symbol's high/low on that day, ordered by symbol
     */
    List<HighLow> findByTradingDate(LocalDate tradingDate);

    /**
     * Finds the high/low of one symbol for one day.
     *
     * @param stockSymbol normalized symbol
     * @param tradingDate trading day
     * @return the high/low, if present
     */
    Optional<HighLow> findBySymbolAndTradingDate(String stockSymbol, LocalDate tradingDate);

    /**
     * Finds the latest trading day of any symbol.
     *
     * @return the most recent trading day with data for any symbol, if any
     */
    Optional<LocalDate> findLatestTradingDate();

    /**
     * Finds the latest trading day of one symbol.
     *
     * @param stockSymbol normalized symbol
     * @return the most recent trading day with data for this symbol, if any
     */
    Optional<LocalDate> findLatestTradingDate(String stockSymbol);

    /**
     * Finds the high/low of one symbol for every day.
     *
     * @param stockSymbol normalized symbol
     * @return every trading day's high/low of the symbol, oldest day first
     */
    List<HighLow> findBySymbol(String stockSymbol);
}
