package com.solactive.stocktracker.exception;

import org.springframework.http.HttpStatus;

/**
 * A file cannot be processed as a whole (HTTP 400). Individual bad rows do
 * <em>not</em> raise this exception; they are skipped and counted in the error summary.
 */
public final class InvalidFileException extends ApiException {

    private InvalidFileException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * No file was sent.
     *
     * @return exception for a request without a file part
     */
    public static InvalidFileException missingFile() {
        return new InvalidFileException("A non-empty CSV file must be supplied in the multipart part 'file'.");
    }

    /**
     * The upload is not a {@code .csv} file.
     *
     * @param fileName file name
     * @return exception for an upload that is not a {@code .csv} file
     */
    public static InvalidFileException notCsv(String fileName) {
        return new InvalidFileException("Only CSV files are supported; '%s' is not a .csv file.".formatted(fileName));
    }

    /**
     * The file has no content or no header line.
     *
     * @param fileName file name
     * @return exception for a zero-byte file or one without a header line
     */
    public static InvalidFileException empty(String fileName) {
        return new InvalidFileException("The file '%s' is empty.".formatted(fileName));
    }

    /**
     * The file could not be read.
     *
     * @param fileName file name
     * @param detail   I/O error detail
     * @return exception for an unreadable stream
     */
    public static InvalidFileException unreadable(String fileName, String detail) {
        return new InvalidFileException("The file '%s' could not be read: %s".formatted(fileName, detail));
    }

    /**
     * The file is not valid CSV.
     *
     * @param fileName file name
     * @param detail   parser error detail
     * @return exception for structurally invalid CSV
     */
    public static InvalidFileException malformed(String fileName, String detail) {
        return new InvalidFileException("The file '%s' is not valid CSV: %s".formatted(fileName, detail));
    }

    /**
     * The header lacks required columns.
     *
     * @param fileName       file name
     * @param missingColumns comma-separated list of missing columns
     * @return exception for a header lacking required columns
     */
    public static InvalidFileException missingHeader(String fileName, String missingColumns) {
        return new InvalidFileException(
                "The file '%s' is missing required header column(s): %s".formatted(fileName, missingColumns));
    }
}
