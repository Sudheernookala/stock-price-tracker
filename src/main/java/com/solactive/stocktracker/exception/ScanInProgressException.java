package com.solactive.stocktracker.exception;

import org.springframework.http.HttpStatus;

/**
 * A folder scan was requested while another one is still running (HTTP 409).
 */
public final class ScanInProgressException extends ApiException {

    /**
     * Creates the exception.
     */
    public ScanInProgressException() {
        super(HttpStatus.CONFLICT, "A folder scan is already running. Follow its progress at "
                + "GET /api/v1/folder-scans/latest and try again when it has finished.");
    }
}
