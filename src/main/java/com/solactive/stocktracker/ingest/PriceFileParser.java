package com.solactive.stocktracker.ingest;

import java.io.InputStream;
import java.util.function.Consumer;

/**
 * Streams a price file row by row.
 *
 * <p>Implementations must not hold the whole file in memory: every row is handed to the
 * {@code sink} as soon as it is read, so files far larger than the heap can be imported.</p>
 */
public interface PriceFileParser {

    /**
     * Parses the given input and pushes each row's validation outcome to {@code sink}.
     *
     * @param fileName name used in error messages
     * @param input    file content; not closed by this method
     * @param sink     receives one {@link ParsedRow} per data row, in file order
     * @throws com.solactive.stocktracker.exception.InvalidFileException when the file as a whole is unusable
     *         (empty, missing required header columns, or structurally malformed)
     */
    void parse(String fileName, InputStream input, Consumer<ParsedRow> sink);
}
