package com.solactive.stocktracker.service;

import com.solactive.stocktracker.dto.FileImportResponse;
import org.springframework.core.io.InputStreamSource;

/**
 * Processes price files into the daily high/low summaries and records each file's status.
 */
public interface PriceImportService {

    /**
     * Processes one file from the folder and records <em>every</em> outcome, because the record is
     * what lets later scans skip the file: {@code COMPLETED}, {@code COMPLETED_WITH_ERRORS},
     * {@code SKIPPED_DUPLICATE}, {@code FAILED} (unusable file) or {@code ERROR} (technical problem).
     *
     * <p>A {@code PROCESSING} record is saved before reading, so a crash mid-file stays visible. The
     * results are saved in the same transaction that marks the file completed (all or nothing).</p>
     *
     * @param fileName file name
     * @param content  file content (read once, to parse it)
     * @param checksum SHA-256 of the content, already computed by the caller
     * @return the final record; never throws for a file-level problem
     * @throws com.solactive.stocktracker.exception.ContentInProgressException when the same content
     *         is being processed right now (nothing is recorded)
     */
    FileImportResponse importFolderFile(String fileName, InputStreamSource content, String checksum);

    /**
     * Processes one uploaded file. Only a <em>usable</em> file is recorded ({@code COMPLETED},
     * {@code COMPLETED_WITH_ERRORS} or {@code SKIPPED_DUPLICATE}). An unusable file (bad header,
     * broken CSV, unreadable) is rejected with an exception and leaves no record, because the caller
     * gets the reason directly in the response.
     *
     * @param fileName file name
     * @param content  file content (read twice: checksum, then parse)
     * @return the record of the processed file
     * @throws com.solactive.stocktracker.exception.InvalidFileException when the file is unusable
     * @throws com.solactive.stocktracker.exception.ContentInProgressException when the same content
     *         is being processed right now (nothing is recorded)
     */
    FileImportResponse importUpload(String fileName, InputStreamSource content);
}
