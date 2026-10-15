package com.solactive.stocktracker.enums;

/**
 * How a file reached the application.
 */
public enum ImportSource {

    /** Uploaded through {@code POST /api/v1/imports}. */
    UPLOAD,

    /** Picked up from the input folder by {@code POST /api/v1/folder-scans}. */
    FOLDER
}
