package com.solactive.stocktracker.enums;

/**
 * Fixed code for why a CSV row was rejected. Used to group rejected rows, because the
 * human-readable message contains the row's value and so differs from row to row.
 */
public enum RejectReason {

    /** A required column is empty. */
    MISSING_FIELD,

    /** Symbol does not match the allowed pattern. */
    INVALID_SYMBOL,

    /** Price is not a decimal number. */
    PRICE_NOT_A_NUMBER,

    /** Price is zero or negative. */
    PRICE_NOT_POSITIVE,

    /** Price has more than 6 decimal places. */
    PRICE_TOO_PRECISE,

    /** Price is above the supported maximum. */
    PRICE_TOO_LARGE,

    /** Timestamp is not ISO 8601 with a time zone. */
    INVALID_TIMESTAMP,

    /** Volume is not a non-negative whole number. */
    INVALID_VOLUME
}
