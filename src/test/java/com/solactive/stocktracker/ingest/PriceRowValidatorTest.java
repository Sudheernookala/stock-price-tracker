package com.solactive.stocktracker.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.solactive.stocktracker.enums.RejectReason;
import com.solactive.stocktracker.support.TestFixtures;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests of {@link PriceRowValidator}, including every malformed row in the sample file.
 */
class PriceRowValidatorTest {

    private PriceRowValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PriceRowValidator(TestFixtures.properties());
    }

    private static RawPriceRow row(String symbol, String price, String ts, String exchange, String volume) {
        return new RawPriceRow(7, symbol, price, ts, exchange, volume);
    }

    @Test
    @DisplayName("valid row is normalised: symbol upper-cased, price scale 6, trading date in NY time")
    void acceptsAndNormalisesValidRow() {
        ParsedRow result = validator.validate(row(" aapl ", "173.4788", "2026-09-16T14:39:32.983730Z", "nasdaq", "181072"));

        assertThat(result).isInstanceOf(ParsedRow.Accepted.class);
        PriceTick tick = ((ParsedRow.Accepted) result).tick();
        assertThat(tick.stockSymbol()).isEqualTo("AAPL");
        assertThat(tick.price()).isEqualTo(new BigDecimal("173.478800"));
        assertThat(tick.price().scale()).isEqualTo(6);
        assertThat(tick.timestamp()).isEqualTo(Instant.parse("2026-09-16T14:39:32.983730Z"));
        assertThat(tick.tradingDate()).isEqualTo(LocalDate.of(2026, 9, 16));
    }

    @Test
    @DisplayName("volume is optional (sample line 102: AAPL row without volume)")
    void acceptsMissingVolume() {
        ParsedRow result = validator.validate(row("AAPL", "180.123456", "2026-09-16T14:39:42.983730Z", "NYSE", null));

        assertThat(result).isInstanceOf(ParsedRow.Accepted.class);
    }

    @Test
    @DisplayName("trading date follows exchange-local time, not UTC")
    void tradingDateUsesExchangeZone() {
        // 01:30 UTC on the 17th is still 21:30 on the 16th in New York.
        ParsedRow result = validator.validate(row("AAPL", "1", "2026-09-17T01:30:00Z", "NYSE", ""));

        assertThat(result).isInstanceOfSatisfying(ParsedRow.Accepted.class,
                accepted -> assertThat(accepted.tick().tradingDate()).isEqualTo(LocalDate.of(2026, 9, 16)));
    }

    @ParameterizedTest(name = "[{index}] {6}")
    @CsvSource(nullValues = "NULL", delimiter = '|', quoteCharacter = '"', textBlock = """
            MSFT | "" | 2026-09-16T14:39:43.983730Z | NASDAQ | 1000000 | MISSING_FIELD | Required field 'price' is missing or blank.
            TSLA | -52.500000 | 2026-09-16T14:39:44.983730Z | NYSE | 500000 | PRICE_NOT_POSITIVE | Price '-52.500000' must be greater than zero.
            GOOGL | 150.000000 | 09/16/2026 14:39:45 | AMEX | 600000 | INVALID_TIMESTAMP | Timestamp '09/16/2026 14:39:45' is not a valid ISO 8601 instant (e.g. 2026-09-16T09:30:00Z).
            AMZN | N/A | 2026-09-16T14:39:46.983730Z | NASDAQ | 700000 | PRICE_NOT_A_NUMBER | Price 'N/A' is not a valid decimal number.
            JPM | 190.000000 | NULL | NULL | NULL | MISSING_FIELD | Required field 'timestamp' is missing or blank.
            AAPL | 1.1234567 | 2026-09-16T14:39:46Z | NYSE | 1 | PRICE_TOO_PRECISE | Price '1.1234567' has more than 6 decimal places.
            AAPL | 1.0 | 2026-09-16T14:39:46 | NYSE | 1 | INVALID_TIMESTAMP | Timestamp '2026-09-16T14:39:46' is not a valid ISO 8601 instant (e.g. 2026-09-16T09:30:00Z).
            AAPL | 1.0 | 2026-09-16T14:39:46Z | NYSE | -5 | INVALID_VOLUME | Volume '-5' must be a non-negative whole number.
            """)
    @DisplayName("invalid rows (the 5 in the sample file, plus key rules) are rejected with a clear reason")
    void rejectsInvalidRows(String symbol, String price, String ts, String exchange, String volume,
                            RejectReason code, String expected) {
        ParsedRow result = validator.validate(row(symbol, price, ts, exchange, volume));

        assertThat(result).isInstanceOfSatisfying(ParsedRow.Rejected.class, rejected -> {
            assertThat(rejected.lineNumber()).isEqualTo(7);
            assertThat(rejected.code()).isEqualTo(code);
            assertThat(rejected.reason()).isEqualTo(expected);
        });
    }

}
