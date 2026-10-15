package com.solactive.stocktracker.service.impl;

import com.solactive.stocktracker.config.AppProperties;
import com.solactive.stocktracker.dto.FileImportResponse;
import com.solactive.stocktracker.dto.FolderScanResponse;
import com.solactive.stocktracker.dto.ImportStats;
import com.solactive.stocktracker.enums.ImportSource;
import com.solactive.stocktracker.enums.ScanStatus;
import com.solactive.stocktracker.exception.ContentInProgressException;
import com.solactive.stocktracker.exception.FolderAccessException;
import com.solactive.stocktracker.exception.InvalidFileException;
import com.solactive.stocktracker.exception.ScanInProgressException;
import com.solactive.stocktracker.service.ChecksumCalculator;
import com.solactive.stocktracker.service.FileImportTracker;
import com.solactive.stocktracker.service.FolderScanService;
import com.solactive.stocktracker.service.PriceImportService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

/**
 * Default {@link FolderScanService}.
 *
 * <p>A scan runs on a background thread, so the caller gets an answer at once instead of waiting
 * for every file. Only one scan runs at a time: a request made meanwhile is rejected. The progress
 * of the running scan (or the result of the last one) is kept in memory and replaced after every
 * file; it only protects and describes a single application instance and is lost on restart. The
 * result of each processed file is stored in the database as usual.</p>
 *
 * <p>Files stay in one folder and are never moved. To avoid processing (and recording) the same
 * file on every scan, each file's SHA-256 checksum is computed once, compared with the stored
 * records and then passed on to the import, so a new file is read only twice (checksum, parse): if
 * the same content was already processed, or already failed because it is unusable, the file is
 * skipped and no record is created. Changing a file changes its checksum, so it is processed again.
 * Files stopped by a technical problem ({@code ERROR}) are retried. A file whose content is being
 * processed right now by another request is skipped without a record and listed as in progress.</p>
 *
 * <p>Files are processed one after another in file-name order, so the order is predictable and one
 * file's problems cannot affect another.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FolderScanServiceImpl implements FolderScanService {

    private static final String CSV_EXTENSION = ".csv";

    private final PriceImportService importService;
    private final ChecksumCalculator checksumCalculator;
    private final FileImportTracker tracker;
    private final AppProperties properties;
    private final TaskExecutor taskExecutor;
    private final Clock clock;

    /** The running scan, or the last finished one; {@code null} before the first scan. */
    private volatile FolderScanResponse latest;

    @Override
    public synchronized FolderScanResponse start() {
        if (latest != null && latest.status() == ScanStatus.RUNNING) {
            throw new ScanInProgressException();
        }
        Path folder = properties.folder().dir();
        // Listing is fast, so it is done before answering: the caller sees how many files will be
        // processed, and an unusable folder is reported at once.
        createDirectory(folder);
        List<Path> files = listCsvFiles(folder);
        FolderScanResponse started = FolderScanResponse.started(folder.toAbsolutePath().toString(), clock.instant())
                .withFilesFound(files.size());
        latest = started;
        log.info("Folder scan of {} started: {} file(s) found", started.folder(), files.size());
        try {
            ScanProgress progress = new ScanProgress(started);
            taskExecutor.execute(() -> run(progress, folder, files));
        } catch (RuntimeException ex) {
            // The scan never started (e.g. the application is shutting down).
            latest = started.finished(ScanStatus.FAILED, "The scan could not be started: " + ex.getMessage(),
                    clock.instant());
            throw ex;
        }
        return started;
    }

    @Override
    public Optional<FolderScanResponse> latest() {
        return Optional.ofNullable(latest);
    }

    private void run(ScanProgress progress, Path folder, List<Path> files) {
        try {
            processFiles(progress, files);
            progress.update(scan -> scan.finished(ScanStatus.COMPLETED, null, clock.instant()));
            FolderScanResponse result = progress.state;
            log.info("Folder scan of {}: {} file(s) found, {} processed, {} failed, {} already processed, {} in progress",
                    result.folder(), result.filesFound(), result.processed().size(), result.failed().size(),
                    result.alreadyProcessed().size(), result.inProgress().size());
        } catch (RuntimeException ex) {
            log.error("Folder scan of {} failed", folder.toAbsolutePath(), ex);
            progress.update(scan -> scan.finished(ScanStatus.FAILED, ex.getMessage(), clock.instant()));
        } finally {
            if (progress.state.status() == ScanStatus.RUNNING) {
                // Only reached for an Error (e.g. out of memory): never leave the scan blocked.
                progress.update(scan -> scan.finished(ScanStatus.FAILED, "The scan stopped unexpectedly",
                        clock.instant()));
            }
        }
    }

    private void processFiles(ScanProgress progress, List<Path> files) {
        for (Path file : files) {
            String fileName = file.getFileName().toString();
            FileSystemResource content = new FileSystemResource(file);
            String checksum;
            try {
                checksum = checksumCalculator.sha256(fileName, content);
            } catch (InvalidFileException ex) {
                FileImportResponse record = recordUnreadable(fileName, ex.getMessage());
                progress.update(scan -> scan.withRecord(record));
                continue;
            }
            if (tracker.isAlreadyHandled(checksum)) {
                log.info("Folder file {} skipped: identical content was already processed (no new record)", fileName);
                progress.update(scan -> scan.withAlreadyProcessed(fileName));
                continue;
            }
            try {
                // The checksum is passed on, so the file is read only once more (to parse it).
                FileImportResponse record = importService.importFolderFile(fileName, content, checksum);
                progress.update(scan -> scan.withRecord(record));
            } catch (ContentInProgressException ex) {
                // The same content is being processed right now (e.g. uploaded); the next scan handles it.
                log.info("Folder file {} skipped: the same content is being processed right now; the next scan "
                        + "handles it", fileName);
                progress.update(scan -> scan.withInProgress(fileName));
            }
        }
    }

    private FileImportResponse recordUnreadable(String fileName, String reason) {
        log.warn("Folder file {} could not be read: {}", fileName, reason);
        return tracker.fail(tracker.start(fileName, ImportSource.FOLDER), null, ImportStats.EMPTY, reason);
    }

    private static List<Path> listCsvFiles(Path folder) {
        try (Stream<Path> entries = Files.list(folder)) {
            return entries
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(CSV_EXTENSION))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException ex) {
            throw new FolderAccessException(folder, ex.toString());
        }
    }

    private static void createDirectory(Path folder) {
        try {
            Files.createDirectories(folder);
        } catch (IOException ex) {
            throw new FolderAccessException(folder, ex.toString());
        }
    }

    /**
     * State of one scan, owned by its background thread. Every change is also published as
     * the latest scan, so a newer scan that replaced it cannot be changed by an older one.
     */
    private final class ScanProgress {

        private FolderScanResponse state;

        private ScanProgress(FolderScanResponse start) {
            this.state = start;
        }

        private void update(UnaryOperator<FolderScanResponse> change) {
            state = change.apply(state);
            latest = state;
        }
    }
}
