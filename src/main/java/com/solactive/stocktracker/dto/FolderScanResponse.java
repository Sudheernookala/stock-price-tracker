package com.solactive.stocktracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.solactive.stocktracker.enums.ImportStatus;
import com.solactive.stocktracker.enums.ScanStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Progress or outcome of a folder scan. Immutable: the running scan publishes a new snapshot after
 * every file.
 *
 * @param status           {@code RUNNING}, {@code COMPLETED} or {@code FAILED}
 * @param folder           folder that is scanned
 * @param filesFound       number of {@code *.csv} files in the folder
 * @param processed        records of the files whose results were saved ({@code COMPLETED} or
 *                         {@code COMPLETED_WITH_ERRORS}), in file-name order
 * @param failed           records of the files that could not be used as a whole, e.g. because of a
 *                         wrong header ({@code FAILED}); the reason is in each record's {@code errorMessage}
 * @param alreadyProcessed names of files skipped because their content was already handled;
 *                         no record is created for them
 * @param inProgress       names of files skipped because the same content is being processed
 *                         right now by another request; the next scan handles them
 * @param errorMessage     why the scan itself failed, if it did
 * @param startedAt        when the scan started
 * @param completedAt      when the scan finished ({@code null} while running)
 */
@Schema(description = "Progress or result of a folder scan")
public record FolderScanResponse(
        ScanStatus status,
        @Schema(example = "/home/me/stock-price-tracker/data/input") String folder,
        @Schema(example = "5") int filesFound,
        List<FileImportResponse> processed,
        List<FileImportResponse> failed,
        @Schema(example = "[\"stock-prices.csv\"]") List<String> alreadyProcessed,
        @Schema(example = "[]") List<String> inProgress,
        @Schema(nullable = true) String errorMessage,
        Instant startedAt,
        @Schema(nullable = true) Instant completedAt) {

    /**
     * Defensive copies so the record stays immutable.
     */
    public FolderScanResponse {
        processed = processed == null ? List.of() : List.copyOf(processed);
        failed = failed == null ? List.of() : List.copyOf(failed);
        alreadyProcessed = alreadyProcessed == null ? List.of() : List.copyOf(alreadyProcessed);
        inProgress = inProgress == null ? List.of() : List.copyOf(inProgress);
    }

    /**
     * Creates the state of a scan that has just started.
     *
     * @param folder    folder to scan
     * @param startedAt start time
     * @return a scan that has just started
     */
    public static FolderScanResponse started(String folder, Instant startedAt) {
        return new FolderScanResponse(ScanStatus.RUNNING, folder, 0, List.of(), List.of(), List.of(), List.of(),
                null, startedAt, null);
    }

    /**
     * Number of files handled so far, in any of the lists.
     *
     * @return files handled so far
     */
    @JsonProperty("filesDone")
    @Schema(example = "5")
    public int filesDone() {
        return processed.size() + failed.size() + alreadyProcessed.size() + inProgress.size();
    }

    /**
     * Sets the number of files the scan will handle.
     *
     * @param count number of files found
     * @return a copy with the number of files found
     */
    public FolderScanResponse withFilesFound(int count) {
        return new FolderScanResponse(status, folder, count, processed, failed, alreadyProcessed, inProgress,
                errorMessage, startedAt, completedAt);
    }

    /**
     * Adds the record of a file this scan handled: to {@code failed} when the file could not be
     * used, otherwise to {@code processed}.
     *
     * @param record record of a file handled by the scan
     * @return a copy with the record added
     */
    public FolderScanResponse withRecord(FileImportResponse record) {
        boolean unusable = record.status() == ImportStatus.FAILED || record.status() == ImportStatus.ERROR;
        return new FolderScanResponse(status, folder, filesFound,
                unusable ? processed : append(processed, record), unusable ? append(failed, record) : failed,
                alreadyProcessed, inProgress, errorMessage, startedAt, completedAt);
    }

    /**
     * Adds a file skipped because its content was already handled.
     *
     * @param fileName file skipped because its content was already handled
     * @return a copy with the name added
     */
    public FolderScanResponse withAlreadyProcessed(String fileName) {
        return new FolderScanResponse(status, folder, filesFound, processed, failed,
                append(alreadyProcessed, fileName), inProgress, errorMessage, startedAt, completedAt);
    }

    /**
     * Adds a file skipped because its content is being processed by another request.
     *
     * @param fileName file skipped because its content is being processed by another request
     * @return a copy with the name added
     */
    public FolderScanResponse withInProgress(String fileName) {
        return new FolderScanResponse(status, folder, filesFound, processed, failed, alreadyProcessed,
                append(inProgress, fileName), errorMessage, startedAt, completedAt);
    }

    /**
     * Marks the scan as finished.
     *
     * @param finalStatus {@code COMPLETED} or {@code FAILED}
     * @param message     why the scan failed, or {@code null}
     * @param finishedAt  end time
     * @return a copy marked as finished
     */
    public FolderScanResponse finished(ScanStatus finalStatus, String message, Instant finishedAt) {
        return new FolderScanResponse(finalStatus, folder, filesFound, processed, failed, alreadyProcessed,
                inProgress, message, startedAt, finishedAt);
    }

    private static <T> List<T> append(List<T> list, T item) {
        List<T> copy = new ArrayList<>(list);
        copy.add(item);
        return copy;
    }
}
