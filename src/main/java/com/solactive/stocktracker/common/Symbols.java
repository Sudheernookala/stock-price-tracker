package com.solactive.stocktracker.common;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Stock symbol rules shared by CSV validation and request validation.
 */
public final class Symbols {

    /** Regex used in request-parameter validation (case-insensitive input). */
    public static final String REQUEST_REGEX = "^[A-Za-z0-9][A-Za-z0-9.\\-]{0,9}$";

    /** Validation message for a symbol that does not match {@link #REQUEST_REGEX}. */
    public static final String REQUEST_MESSAGE = "symbol must be 1-10 characters: letters, digits, '.' or '-'";

    private static final Pattern NORMALISED = Pattern.compile("^[A-Z0-9][A-Z0-9.\\-]{0,9}$");

    private Symbols() {
    }

    /**
     * Normalizes a symbol to its canonical form (trimmed, upper case).
     *
     * @param raw raw symbol, may be {@code null}
     * @return canonical symbol, or {@code null} when {@code raw} is {@code null}
     */
    public static String normalize(String raw) {
        return raw == null ? null : raw.strip().toUpperCase(Locale.ROOT);
    }

    /**
     * Checks whether an already normalized symbol is well-formed, e.g. {@code AAPL} or {@code BRK.B}.
     *
     * @param normalised symbol returned by {@link #normalize(String)}
     * @return {@code true} when valid
     */
    public static boolean isValid(String normalised) {
        return normalised != null && NORMALISED.matcher(normalised).matches();
    }
}
