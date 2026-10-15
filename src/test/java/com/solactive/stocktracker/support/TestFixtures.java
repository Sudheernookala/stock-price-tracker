package com.solactive.stocktracker.support;

import com.solactive.stocktracker.config.AppProperties;
import com.solactive.stocktracker.ingest.HighLow;
import com.solactive.stocktracker.ingest.PriceTick;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Shared builders for unit tests.
 */
public final class TestFixtures {

    /** Trading day of the sample data. */
    public static final LocalDate DAY = LocalDate.of(2026, 9, 16);

    private TestFixtures() {
    }

    /**
     * @param folder folder scanned by the folder scan
     * @return properties with the New York trading zone and the given folder
     */
    public static AppProperties properties(Path folder) {
        return new AppProperties(ZoneId.of("America/New_York"), new AppProperties.Folder(folder));
    }

    /**
     * @return properties with the New York trading zone
     */
    public static AppProperties properties() {
        return properties(Path.of("in"));
    }

    /**
     * @param symbol    symbol
     * @param price     price text
     * @param timestamp ISO instant text
     * @return a tick on {@link #DAY}
     */
    public static PriceTick tick(String symbol, String price, String timestamp) {
        return new PriceTick(symbol, new BigDecimal(price).setScale(6), Instant.parse(timestamp), DAY);
    }

    /**
     * @param symbol   symbol
     * @param high     high price
     * @param highTime high timestamp
     * @param low      low price
     * @param lowTime  low timestamp
     * @return a high/low on {@link #DAY}
     */
    public static HighLow highLow(String symbol, String high, String highTime, String low, String lowTime) {
        return new HighLow(symbol, DAY, new BigDecimal(high).setScale(6), Instant.parse(highTime),
                new BigDecimal(low).setScale(6), Instant.parse(lowTime));
    }
}
