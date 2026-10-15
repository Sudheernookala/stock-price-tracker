package com.solactive.stocktracker.service.impl;

import com.solactive.stocktracker.dto.ErrorGroup;
import com.solactive.stocktracker.dto.FileImportResponse;
import com.solactive.stocktracker.enums.ImportSource;
import com.solactive.stocktracker.enums.RejectReason;
import com.solactive.stocktracker.enums.ImportStatus;
import com.solactive.stocktracker.exception.InvalidFileException;
import com.solactive.stocktracker.ingest.HighLowAggregator;
import com.solactive.stocktracker.ingest.ParsedRow;
import com.solactive.stocktracker.ingest.PriceFileParser;
import com.solactive.stocktracker.ingest.PriceTick;
import com.solactive.stocktracker.repository.SummaryRepository;
import com.solactive.stocktracker.service.ChecksumCalculator;
import com.solactive.stocktracker.service.ContentClaims;
import com.solactive.stocktracker.service.FileImportTracker;
import com.solactive.stocktracker.dto.ImportStats;
import com.solactive.stocktracker.service.PriceImportService;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Default {@link PriceImportService}.
 *
 * <p>Flow: checksum &rarr; skip if already processed &rarr; stream-parse rows into a per-file
 * aggregator (one entry per symbol and day, so memory does not grow with file size) &rarr; in
 * <em>one</em> transaction, merge the results into {@code daily_summary} and record the file as
 * completed.</p>
 *
 * <p>The content is claimed ({@link ContentClaims}) for the whole time it is processed, so the same
 * content arriving twice at once (two uploads, or an upload and a folder scan) is processed only
 * once; the second gets a {@link com.solactive.stocktracker.exception.ContentInProgressException}.
 * The claim is released after the results are committed, also when processing fails.</p>
 *
 * <p>Rejected rows are not stored one by one; they are counted by reason ({@link ErrorGroup}), so a
 * file with many bad rows still shows every kind of problem in a few lines.</p>
 *
 * <p>Folder files are recorded from the start ({@code PROCESSING}) and every outcome is kept,
 * including {@code FAILED} and {@code ERROR}, so later scans can skip them. Uploads are recorded
 * only when usable: an unusable upload is rejected with the reason and leaves nothing behind.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceImportServiceImpl implements PriceImportService {

    private final PriceFileParser parser;
    private final ChecksumCalculator checksumCalculator;
    private final FileImportTracker tracker;
    private final ContentClaims contentClaims;
    private final SummaryRepository summaryRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Override
    @SuppressWarnings("try")   // the claim is only held for the block and released by close()
    public FileImportResponse importFolderFile(String fileName, InputStreamSource content, String checksum) {
        try (ContentClaims.Claim claim = contentClaims.claim(checksum)) {
            long id = tracker.start(fileName, ImportSource.FOLDER);
            ImportRun run = new ImportRun();
            try {
                if (tracker.isAlreadyProcessed(checksum)) {
                    return logged(tracker.skipDuplicate(id, checksum));
                }
                log.info("Processing file '{}' (id={}, source=FOLDER)", fileName, id);
                read(fileName, content, run);
                return logged(transactionTemplate.execute(status -> {
                    summaryRepository.mergeAll(run.aggregator.results());
                    return tracker.complete(id, checksum, run.stats());
                }));
            } catch (InvalidFileException ex) {
                return logged(tracker.fail(id, checksum, run.stats(), ex.getMessage()));
            } catch (RuntimeException ex) {
                markErrorQuietly(fileName, id, checksum, run, ex);
                throw ex;
            }
        }
    }

    @Override
    @SuppressWarnings("try")   // the claim is only held for the block and released by close()
    public FileImportResponse importUpload(String fileName, InputStreamSource content) {
        Instant startedAt = clock.instant();
        String checksum = checksumCalculator.sha256(fileName, content);
        try (ContentClaims.Claim claim = contentClaims.claim(checksum)) {
            if (tracker.isAlreadyProcessed(checksum)) {
                return logged(tracker.recordUploadDuplicate(fileName, checksum, startedAt));
            }
            ImportRun run = new ImportRun();
            log.info("Processing file '{}' (source=UPLOAD)", fileName);
            read(fileName, content, run);   // an unusable file throws here, before anything is recorded
            return logged(transactionTemplate.execute(status -> {
                summaryRepository.mergeAll(run.aggregator.results());
                return tracker.recordUploadCompleted(fileName, checksum, startedAt, run.stats());
            }));
        }
    }

    private void read(String fileName, InputStreamSource content, ImportRun run) {
        try (InputStream in = content.getInputStream()) {
            parser.parse(fileName, in, run::accept);
        } catch (IOException ex) {
            throw InvalidFileException.unreadable(fileName, ex.getMessage());
        }
    }

    private void markErrorQuietly(String fileName, long id, String checksum, ImportRun run, RuntimeException cause) {
        // The stack trace is logged by the caller that handles the exception.
        log.error("File '{}' (id={}) was stopped by a technical problem and is marked ERROR; the next folder scan "
                + "retries it: {}", fileName, id, cause.getMessage());
        try {
            tracker.error(id, checksum, run.stats(), "Unexpected error: " + cause.getMessage());
        } catch (RuntimeException ex) {
            // The database itself may be the problem; the record then stays PROCESSING.
            log.error("Could not mark file import {} as ERROR", id, ex);
        }
    }

    private static FileImportResponse logged(FileImportResponse result) {
        if (result.status() == ImportStatus.FAILED) {
            log.warn("File '{}' (id={}, source={}): FAILED - {}", result.fileName(), result.id(), result.source(),
                    result.errorMessage());
        } else {
            log.info("File '{}' (id={}, source={}): {} - {} rows, {} accepted, {} rejected, {} ms",
                    result.fileName(), result.id(), result.source(), result.status(), result.totalRows(),
                    result.acceptedRows(), result.rejectedRows(), result.durationMs());
        }
        return result;
    }

    /**
     * Mutable state of one file being read. Confined to the calling thread.
     */
    private static final class ImportRun {

        private final Map<RejectReason, GroupCount> groups = new EnumMap<>(RejectReason.class);
        private final HighLowAggregator aggregator = new HighLowAggregator();
        private long accepted;
        private long rejected;

        private void accept(ParsedRow row) {
            switch (row) {
                case ParsedRow.Accepted(long line, PriceTick tick) -> {
                    accepted++;
                    aggregator.accept(tick);
                }
                case ParsedRow.Rejected(long line, RejectReason code, String reason) -> {
                    rejected++;
                    groups.computeIfAbsent(code, c -> new GroupCount(line, reason)).count++;
                    log.debug("Rejected line {}: {}", line, reason);
                }
            }
        }

        private ImportStats stats() {
            List<ErrorGroup> summary = groups.entrySet().stream()
                    .map(e -> new ErrorGroup(e.getKey(), e.getValue().count, e.getValue().firstLine, e.getValue().example))
                    .sorted(Comparator.comparingLong(ErrorGroup::count).reversed()
                            .thenComparingLong(ErrorGroup::firstLine))
                    .toList();
            return new ImportStats(accepted + rejected, accepted, rejected, summary);
        }

        /** Running count of one reason; the first row is kept as the example. */
        private static final class GroupCount {

            private final long firstLine;
            private final String example;
            private long count;

            private GroupCount(long firstLine, String example) {
                this.firstLine = firstLine;
                this.example = example;
            }
        }
    }
}
