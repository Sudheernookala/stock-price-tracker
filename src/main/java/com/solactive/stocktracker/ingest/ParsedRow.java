package com.solactive.stocktracker.ingest;

import com.solactive.stocktracker.enums.RejectReason;

/**
 * Result of validating one CSV row. Sealed so that consumers handle every outcome with an
 * exhaustive {@code switch} and the compiler flags any new outcome that is not handled.
 */
public sealed interface ParsedRow permits ParsedRow.Accepted, ParsedRow.Rejected {

    /**
     * Line number of the row.
     *
     * @return 1-based line number of the row in the source file
     */
    long lineNumber();

    /**
     * A row that passed validation.
     *
     * @param lineNumber 1-based line number
     * @param tick       the validated tick
     */
    record Accepted(long lineNumber, PriceTick tick) implements ParsedRow {
    }

    /**
     * A row that failed validation and is skipped.
     *
     * @param lineNumber 1-based line number
     * @param code       fixed code of the reason, used to group rejected rows
     * @param reason     human-readable reason
     */
    record Rejected(long lineNumber, RejectReason code, String reason) implements ParsedRow {
    }
}
