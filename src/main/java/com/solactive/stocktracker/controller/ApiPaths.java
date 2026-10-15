package com.solactive.stocktracker.controller;

/**
 * Versioned URL roots of the REST API.
 */
public final class ApiPaths {

    private static final String V1 = "/api/v1";
    /** Import resource collection. */
    public static final String IMPORTS = V1 + "/imports";
    /** Folder scan resource collection. */
    public static final String FOLDER_SCANS = V1 + "/folder-scans";
    /** Daily summary resource collection. */
    public static final String SUMMARIES = V1 + "/summaries";

    private ApiPaths() {
    }
}
