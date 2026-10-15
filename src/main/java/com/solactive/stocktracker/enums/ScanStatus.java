package com.solactive.stocktracker.enums;

/**
 * Lifecycle of a folder scan.
 */
public enum ScanStatus {

    /** The scan is processing the files in the background. */
    RUNNING,

    /** Every file was looked at. Individual files may still have failed; see their records. */
    COMPLETED,

    /** The scan itself stopped part-way, e.g. because the database became unavailable. */
    FAILED
}
