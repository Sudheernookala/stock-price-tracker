package com.solactive.stocktracker.ingest;

import com.solactive.stocktracker.common.Symbols;
import com.solactive.stocktracker.config.AppProperties;
import com.solactive.stocktracker.enums.RejectReason;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Validates and normalises a {@link RawPriceRow} into a {@link PriceTick}.
 *
 * <p>Policy (a row is either fully trusted or skipped; values are never guessed or repaired):</p>
 * <ul>
 *   <li>{@code stockSymbol}, {@code price}, {@code timestamp} and {@code exchange} are required.</li>
 *   <li>Symbols are trimmed and upper-cased, then must match {@code [A-Z0-9][A-Z0-9.-]{0,9}}.</li>
 *   <li>Price must be a decimal number, strictly positive, with at most 6 decimal places and at
 *       most 12 integer digits. It is never rounded.</li>
 *   <li>Timestamp must be ISO 8601 <em>with</em> an offset or {@code Z}; local times such as
 *       {@code 09/16/2026 14:39:45} are rejected because their time zone is ambiguous.
 *       Sub-microsecond digits are truncated (the data has microsecond precision).</li>
 *   <li>Volume is optional; when present it must be a non-negative whole number.</li>
 * </ul>
 *
 * <p>The trading day of a tick is its date in the exchange's time zone ({@code app.trading-zone},
 * default {@code America/New_York}), not the UTC date, so an after-hours trade at 20:00 New York
 * time stays on its trading day although it is already the next day in UTC.</p>
 */
@Component
@RequiredArgsConstructor
public class PriceRowValidator {

    private static final int PRICE_SCALE = 6;
    private static final BigDecimal MAX_PRICE = new BigDecimal("999999999999.999999");

    private final AppProperties properties;

    /**
     * Validates a raw row.
     *
     * @param row raw CSV values
     * @return {@link ParsedRow.Accepted} with a normalised tick, or {@link ParsedRow.Rejected}
     *         carrying the first validation failure
     */
    public ParsedRow validate(RawPriceRow row) {
        long line = row.lineNumber();

        if (isBlank(row.stockSymbol())) {
            return missing(line, "stockSymbol");
        }
        if (isBlank(row.price())) {
            return missing(line, "price");
        }
        if (isBlank(row.timestamp())) {
            return missing(line, "timestamp");
        }
        if (isBlank(row.exchange())) {
            return missing(line, "exchange");
        }

        String symbol = Symbols.normalize(row.stockSymbol());
        if (!Symbols.isValid(symbol)) {
            return reject(line, RejectReason.INVALID_SYMBOL, "Stock symbol '%s' is invalid (expected 1-10 characters: letters, digits, '.' or '-').", row.stockSymbol());
        }

        BigDecimal price;
        try {
            price = new BigDecimal(row.price().strip());
        } catch (NumberFormatException ex) {
            return reject(line, RejectReason.PRICE_NOT_A_NUMBER, "Price '%s' is not a valid decimal number.", row.price());
        }
        if (price.signum() <= 0) {
            return reject(line, RejectReason.PRICE_NOT_POSITIVE, "Price '%s' must be greater than zero.", row.price());
        }
        if (price.stripTrailingZeros().scale() > PRICE_SCALE) {
            return reject(line, RejectReason.PRICE_TOO_PRECISE, "Price '%s' has more than 6 decimal places.", row.price());
        }
        if (price.compareTo(MAX_PRICE) > 0) {
            return reject(line, RejectReason.PRICE_TOO_LARGE, "Price '%s' exceeds the supported maximum of 999999999999.999999.", row.price());
        }

        Instant timestamp;
        try {
            timestamp = OffsetDateTime.parse(row.timestamp().strip()).toInstant().truncatedTo(ChronoUnit.MICROS);
        } catch (DateTimeParseException ex) {
            return reject(line, RejectReason.INVALID_TIMESTAMP, "Timestamp '%s' is not a valid ISO 8601 instant (e.g. 2026-09-16T09:30:00Z).", row.timestamp());
        }

        if (!isBlank(row.volume()) && !isWholeNumber(row.volume())) {
            return reject(line, RejectReason.INVALID_VOLUME, "Volume '%s' must be a non-negative whole number.", row.volume());
        }

        PriceTick tick = new PriceTick(
                symbol,
                price.setScale(PRICE_SCALE, RoundingMode.UNNECESSARY),
                timestamp,
                LocalDate.ofInstant(timestamp, properties.tradingZone()));
        return new ParsedRow.Accepted(line, tick);
    }

    private static ParsedRow missing(long line, String field) {
        return reject(line, RejectReason.MISSING_FIELD, "Required field '%s' is missing or blank.", field);
    }

    private static ParsedRow reject(long line, RejectReason code, String template, Object value) {
        return new ParsedRow.Rejected(line, code, template.formatted(value));
    }

    private static boolean isWholeNumber(String value) {
        try {
            return Long.parseLong(value.strip()) >= 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
