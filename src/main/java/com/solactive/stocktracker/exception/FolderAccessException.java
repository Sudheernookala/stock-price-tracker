package com.solactive.stocktracker.exception;

import java.nio.file.Path;
import org.springframework.http.HttpStatus;

/**
 * The configured folder cannot be created or listed (HTTP 500: a server setup problem,
 * not a client error).
 */
public final class FolderAccessException extends ApiException {

    /**
     * Creates the exception.
     *
     * @param folder the folder
     * @param detail I/O error detail
     */
    public FolderAccessException(Path folder, String detail) {
        super(HttpStatus.INTERNAL_SERVER_ERROR,
                "The folder '%s' cannot be accessed: %s".formatted(folder.toAbsolutePath(), detail));
    }
}
