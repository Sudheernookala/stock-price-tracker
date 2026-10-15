package com.solactive.stocktracker.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base type of all business exceptions that map to an HTTP error response. The hierarchy is
 * sealed so the global exception handler can map every subtype exhaustively.
 */
@Getter
public abstract sealed class ApiException extends RuntimeException
        permits InvalidFileException, ResourceNotFoundException, ScanInProgressException, FolderAccessException,
                ContentInProgressException {

    /** HTTP status of the error response. */
    private final HttpStatus status;

    /**
     * Creates the exception.
     *
     * @param status  HTTP status to return
     * @param message client-facing message
     */
    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
