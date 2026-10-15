package com.solactive.stocktracker.exception;

import java.time.LocalDate;
import org.springframework.http.HttpStatus;

/**
 * A requested resource does not exist (HTTP 404).
 */
public final class ResourceNotFoundException extends ApiException {

    private ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }

    /**
     * No data for a symbol on a given day.
     *
     * @param symbol      normalised symbol
     * @param tradingDate trading day
     * @return exception for a symbol without data on that day
     */
    public static ResourceNotFoundException summary(String symbol, LocalDate tradingDate) {
        return new ResourceNotFoundException(
                "No high/low summary found for symbol '%s' on %s.".formatted(symbol, tradingDate));
    }

    /**
     * No data for a symbol at all.
     *
     * @param symbol normalised symbol
     * @return exception for a symbol without any data
     */
    public static ResourceNotFoundException symbol(String symbol) {
        return new ResourceNotFoundException("No high/low data found for symbol '%s'.".formatted(symbol));
    }

    /**
     * Unknown processing record.
     *
     * @param id processing record id
     * @return exception for an unknown processing record
     */
    public static ResourceNotFoundException fileImport(long id) {
        return new ResourceNotFoundException("No file import found with id %d.".formatted(id));
    }

    /**
     * No folder scan to report.
     *
     * @return exception for a progress request before any folder scan has run
     */
    public static ResourceNotFoundException folderScan() {
        return new ResourceNotFoundException("No folder scan has run since the application started.");
    }
}
