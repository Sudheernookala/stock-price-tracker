package com.solactive.stocktracker.service;

import com.solactive.stocktracker.dto.FolderScanResponse;
import java.util.Optional;

/**
 * Processes the CSV files in the configured folder, in the background.
 */
public interface FolderScanService {

    /**
     * Starts a scan of the configured folder and returns at once; the {@code *.csv} files are
     * processed in the background, one after another in file-name order. Files stay in the folder.
     * A file whose exact content was already processed (or already failed because it is unusable)
     * is skipped without creating a record. Only one scan runs at a time.
     *
     * @return the scan that has just started (status {@code RUNNING}, with the number of files found)
     * @throws com.solactive.stocktracker.exception.ScanInProgressException when a scan is already running
     * @throws com.solactive.stocktracker.exception.FolderAccessException   when the folder cannot be used
     */
    FolderScanResponse start();

    /**
     * Returns the latest scan.
     *
     * @return the progress of the running scan, or the result of the last finished one; empty when
     *         no scan has run since the application started
     */
    Optional<FolderScanResponse> latest();
}
