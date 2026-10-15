package com.solactive.stocktracker.ingest;

/**
 * One CSV data row exactly as read, before validation. Any field may be {@code null} (column
 * absent in a short row) or blank.
 *
 * @param lineNumber 1-based line number in the source file
 * @param stockSymbol raw symbol text
 * @param price       raw price text
 * @param timestamp   raw timestamp text
 * @param exchange    raw exchange text
 * @param volume      raw volume text
 */
public record RawPriceRow(
        long lineNumber,
        String stockSymbol,
        String price,
        String timestamp,
        String exchange,
        String volume) {
}
