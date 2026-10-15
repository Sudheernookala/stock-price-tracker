package com.solactive.stocktracker.service;

import com.solactive.stocktracker.dto.ErrorGroup;
import com.solactive.stocktracker.dto.FileImportResponse;
import com.solactive.stocktracker.dto.ImportStats;
import com.solactive.stocktracker.entity.FileImport;
import com.solactive.stocktracker.enums.ImportSource;
import com.solactive.stocktracker.enums.ImportStatus;
import com.solactive.stocktracker.repository.FileImportRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the processing status of each file in table {@code file_import}.
 *
 * <p>Each method runs in its own transaction unless the caller already has one. For folder files
 * the {@code PROCESSING} record is committed <em>before</em> the file is read, so a file that
 * crashes the application half-way stays visible. Uploads are recorded only once they have been
 * processed successfully.</p>
 */
@Component
@RequiredArgsConstructor
public class FileImportTracker {

    private static final Set<ImportStatus> SUCCESSFUL =
            EnumSet.of(ImportStatus.COMPLETED, ImportStatus.COMPLETED_WITH_ERRORS);
    private static final Set<ImportStatus> FINISHED_FOR_GOOD =
            EnumSet.of(ImportStatus.COMPLETED, ImportStatus.COMPLETED_WITH_ERRORS, ImportStatus.FAILED);
    private static final int MAX_FILE_NAME = 255;
    private static final int MAX_MESSAGE = 2000;
    private static final int MAX_EXAMPLE = 500;
    private static final String DUPLICATE_MESSAGE =
            "Identical content was already processed, so the file was not read again.";

    private final FileImportRepository repository;
    private final Clock clock;

    /**
     * Creates a {@code PROCESSING} record.
     *
     * @param fileName file name
     * @param source   how the file arrived
     * @return id of the new record
     */
    @Transactional
    public long start(String fileName, ImportSource source) {
        return repository.save(newRecord(fileName, source, clock.instant())).getId();
    }

    /**
     * Records a successfully processed upload. Called in the transaction that saves its results.
     *
     * @param fileName  file name
     * @param checksum  SHA-256 of the content
     * @param startedAt when processing started
     * @param stats     row counts and error summary
     * @return the new record
     */
    @Transactional
    public FileImportResponse recordUploadCompleted(String fileName, String checksum, Instant startedAt,
                                                    ImportStats stats) {
        return finish(newRecord(fileName, ImportSource.UPLOAD, startedAt), completedStatus(stats), checksum, stats, null);
    }

    /**
     * Records an upload whose content was already processed, so nothing was counted twice.
     *
     * @param fileName  file name
     * @param checksum  SHA-256 of the content
     * @param startedAt when processing started
     * @return the new record
     */
    @Transactional
    public FileImportResponse recordUploadDuplicate(String fileName, String checksum, Instant startedAt) {
        return finish(newRecord(fileName, ImportSource.UPLOAD, startedAt), ImportStatus.SKIPPED_DUPLICATE,
                checksum, ImportStats.EMPTY, DUPLICATE_MESSAGE);
    }

    /**
     * Used by uploads and folder files to avoid counting the same content twice.
     *
     * @param checksum SHA-256 of a file
     * @return {@code true} if identical content was already processed successfully
     */
    @Transactional(readOnly = true)
    public boolean isAlreadyProcessed(String checksum) {
        return repository.existsByChecksumAndStatusIn(checksum, SUCCESSFUL);
    }

    /**
     * Used by the folder scan to leave files alone that need no further work.
     *
     * @param checksum SHA-256 of a file
     * @return {@code true} if identical content was already processed successfully, or already
     *         failed because the content itself is unusable (retrying would fail the same way)
     */
    @Transactional(readOnly = true)
    public boolean isAlreadyHandled(String checksum) {
        return repository.existsByChecksumAndStatusIn(checksum, FINISHED_FOR_GOOD);
    }

    /**
     * Marks a file as successfully processed and stores its error summary.
     *
     * @param id       record id
     * @param checksum SHA-256 of the content
     * @param stats    row counts and error summary
     * @return the updated record
     */
    @Transactional
    public FileImportResponse complete(long id, String checksum, ImportStats stats) {
        return finish(load(id), completedStatus(stats), checksum, stats, null);
    }

    /**
     * Marks a file as a duplicate of an earlier successful one.
     *
     * @param id       record id
     * @param checksum SHA-256 of the content
     * @return the updated record
     */
    @Transactional
    public FileImportResponse skipDuplicate(long id, String checksum) {
        return finish(load(id), ImportStatus.SKIPPED_DUPLICATE, checksum, ImportStats.EMPTY, DUPLICATE_MESSAGE);
    }

    /**
     * Marks a file as failed. Nothing from the file was saved.
     *
     * @param id       record id
     * @param checksum SHA-256 of the content, or {@code null} if unknown
     * @param stats    rows read before the failure
     * @param message  why the file failed
     * @return the updated record
     */
    @Transactional
    public FileImportResponse fail(long id, String checksum, ImportStats stats, String message) {
        return finish(load(id), ImportStatus.FAILED, checksum, stats, message);
    }

    /**
     * Marks a file as stopped by a technical problem. Nothing from the file was saved, and the
     * folder scan will try it again.
     *
     * @param id       record id
     * @param checksum SHA-256 of the content, or {@code null} if unknown
     * @param stats    rows read before the problem
     * @param message  what went wrong
     * @return the updated record
     */
    @Transactional
    public FileImportResponse error(long id, String checksum, ImportStats stats, String message) {
        return finish(load(id), ImportStatus.ERROR, checksum, stats, message);
    }

    private static FileImport newRecord(String fileName, ImportSource source, Instant startedAt) {
        return FileImport.builder()
                .fileName(truncate(fileName, MAX_FILE_NAME))
                .source(source)
                .status(ImportStatus.PROCESSING)
                .startedAt(startedAt)
                .build();
    }

    private static ImportStatus completedStatus(ImportStats stats) {
        return stats.rejectedRows() == 0 ? ImportStatus.COMPLETED : ImportStatus.COMPLETED_WITH_ERRORS;
    }

    private FileImportResponse finish(FileImport record, ImportStatus status, String checksum, ImportStats stats,
                                      String message) {
        record.setStatus(status);
        record.setChecksum(checksum);
        record.setTotalRows(stats.totalRows());
        record.setAcceptedRows(stats.acceptedRows());
        record.setRejectedRows(stats.rejectedRows());
        record.setErrorMessage(truncate(message, MAX_MESSAGE));
        record.setCompletedAt(clock.instant());
        for (ErrorGroup group : stats.errorSummary()) {
            record.addErrorGroup(group.code(), group.count(), group.firstLine(), truncate(group.example(), MAX_EXAMPLE));
        }
        return FileImportResponse.from(repository.save(record), true);
    }

    private FileImport load(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalStateException("File import record " + id + " disappeared"));
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
