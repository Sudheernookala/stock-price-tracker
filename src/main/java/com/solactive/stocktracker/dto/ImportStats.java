package com.solactive.stocktracker.dto;

import java.util.List;

/**
 * Row counts of one file, plus its rejected rows counted by reason.
 *
 * @param totalRows    data rows read
 * @param acceptedRows valid rows
 * @param rejectedRows invalid rows
 * @param errorSummary every rejected row counted by reason, largest group first
 */
public record ImportStats(long totalRows, long acceptedRows, long rejectedRows, List<ErrorGroup> errorSummary) {

    /** Statistics of a file that was not read. */
    public static final ImportStats EMPTY = new ImportStats(0, 0, 0, List.of());

    /**
     * Defensive copy so the record stays immutable.
     */
    public ImportStats {
        errorSummary = errorSummary == null ? List.of() : List.copyOf(errorSummary);
    }
}
