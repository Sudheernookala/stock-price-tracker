package com.solactive.stocktracker.exception;

import org.springframework.http.HttpStatus;

/**
 * A file with the same content is being processed right now (HTTP 409). The caller can try again
 * once it has finished; the file will then be recognised as already processed.
 */
public final class ContentInProgressException extends ApiException {

    /**
     * Creates the exception.
     */
    public ContentInProgressException() {
        super(HttpStatus.CONFLICT,
                "A file with the same content is already being processed. Try again when it has finished.");
    }
}
