package com.solactive.stocktracker.enums;

/**
 * Lifecycle of a processed file.
 */
public enum ImportStatus {

    /** The file is being read. A row left in this state means the application stopped mid-file. */
    PROCESSING,

    /** Every row was valid and the results were saved. */
    COMPLETED,

    /** The results were saved, but some rows were rejected (see the error summary). */
    COMPLETED_WITH_ERRORS,

    /**
     * The file as a whole could not be used (empty, bad header, broken CSV). Nothing was saved.
     * The folder scan does not retry it until its content changes.
     */
    FAILED,

    /**
     * A technical problem (for example, the database was unavailable) stopped the file. Nothing was
     * saved. The folder scan retries it on the next run.
     */
    ERROR,

    /** Identical content was already processed successfully, so the file was not read again. */
    SKIPPED_DUPLICATE
}
