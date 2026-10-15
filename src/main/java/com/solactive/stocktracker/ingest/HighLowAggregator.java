package com.solactive.stocktracker.ingest;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Single-pass, in-memory high/low aggregation for one import.
 *
 * <p>Memory is O(symbols x trading days), independent of the number of rows, so a file with
 * millions of ticks for ten symbols needs ten entries. Not thread-safe: one instance is created
 * per import and fed sequentially.</p>
 */
public class HighLowAggregator {

    private final Map<SummaryKey, HighLow> results = new HashMap<>();

    /**
     * Folds a tick into the running high/low of its symbol and trading day.
     *
     * @param tick validated tick
     */
    public void accept(PriceTick tick) {
        results.merge(SummaryKey.of(tick), HighLow.of(tick), HighLow::merge);
    }

    /**
     * Returns the aggregated results.
     *
     * @return one {@link HighLow} per symbol and trading day, in no particular order
     */
    public Collection<HighLow> results() {
        return List.copyOf(results.values());
    }
}
