package com.solactive.stocktracker.ingest;

import static com.solactive.stocktracker.support.TestFixtures.highLow;
import static com.solactive.stocktracker.support.TestFixtures.tick;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests of {@link HighLow} and {@link HighLowAggregator}.
 */
class HighLowAggregatorTest {

    @Test
    @DisplayName("out-of-order input: an earlier tick arriving last can still be the high")
    void outOfOrderInput() {
        var aggregator = new HighLowAggregator();
        aggregator.accept(tick("NVDA", "126.675177", "2026-09-16T14:39:32Z"));
        aggregator.accept(tick("NVDA", "120.417882", "2026-09-16T14:39:40Z"));
        aggregator.accept(tick("NVDA", "999.999999", "2026-09-16T13:00:00Z"));

        HighLow result = aggregator.results().iterator().next();
        assertThat(result.highPrice()).isEqualByComparingTo("999.999999");
        assertThat(result.highTimestamp()).isEqualTo(Instant.parse("2026-09-16T13:00:00Z"));
        assertThat(result.lowPrice()).isEqualByComparingTo("120.417882");
        assertThat(result.lowTimestamp()).isEqualTo(Instant.parse("2026-09-16T14:39:40Z"));
    }

    @Test
    @DisplayName("ties keep the earliest timestamp regardless of arrival order")
    void tiesKeepEarliestTimestamp() {
        var late = tick("MSFT", "400", "2026-09-16T15:00:00Z");
        var early = tick("MSFT", "400", "2026-09-16T14:00:00Z");

        var forward = new HighLowAggregator();
        forward.accept(early);
        forward.accept(late);
        var backward = new HighLowAggregator();
        backward.accept(late);
        backward.accept(early);

        assertThat(forward.results()).isEqualTo(backward.results());
        HighLow result = forward.results().iterator().next();
        assertThat(result.highTimestamp()).isEqualTo(early.timestamp());
        assertThat(result.lowTimestamp()).isEqualTo(early.timestamp());
    }

    @Test
    @DisplayName("symbols and trading days are aggregated independently")
    void separatesSymbolsAndDays() {
        var aggregator = new HighLowAggregator();
        aggregator.accept(tick("AAPL", "10", "2026-09-16T14:00:00Z"));
        aggregator.accept(tick("MSFT", "20", "2026-09-16T14:00:00Z"));
        aggregator.accept(new PriceTick("AAPL", new BigDecimal("30.000000"), Instant.parse("2026-09-17T14:00:00Z"),
                LocalDate.of(2026, 9, 17)));

        assertThat(aggregator.results()).hasSize(3);
    }

    @Test
    @DisplayName("result is independent of input order (100 random shuffles)")
    void orderIndependent() {
        List<PriceTick> ticks = new ArrayList<>();
        Random random = new Random(42);
        for (int i = 0; i < 500; i++) {
            String price = BigDecimal.valueOf(100 + random.nextInt(20)).toPlainString(); // many ties
            ticks.add(tick(i % 2 == 0 ? "AAPL" : "TSLA", price, Instant.parse("2026-09-16T14:00:00Z").plusSeconds(i).toString()));
        }
        var expected = new HashSet<>(aggregate(ticks));

        for (int run = 0; run < 100; run++) {
            Collections.shuffle(ticks, random);
            assertThat(new HashSet<>(aggregate(ticks))).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("merging two results keeps the higher high and the lower low")
    void mergesResults() {
        HighLow first = highLow("AAPL", "180", "2026-09-16T14:00:00Z", "150", "2026-09-16T14:05:00Z");
        HighLow second = highLow("AAPL", "190", "2026-09-16T15:00:00Z", "155", "2026-09-16T15:05:00Z");

        HighLow merged = first.merge(second);

        assertThat(merged).isEqualTo(highLow("AAPL", "190", "2026-09-16T15:00:00Z", "150", "2026-09-16T14:05:00Z"));
        assertThat(second.merge(first)).isEqualTo(merged);
    }

    private static List<HighLow> aggregate(List<PriceTick> ticks) {
        var aggregator = new HighLowAggregator();
        ticks.forEach(aggregator::accept);
        return List.copyOf(aggregator.results());
    }
}
