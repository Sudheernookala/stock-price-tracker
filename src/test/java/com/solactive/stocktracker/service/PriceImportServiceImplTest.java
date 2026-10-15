package com.solactive.stocktracker.service;

import static com.solactive.stocktracker.support.TestFixtures.tick;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.solactive.stocktracker.dto.ErrorGroup;
import com.solactive.stocktracker.dto.FileImportResponse;
import com.solactive.stocktracker.dto.ImportStats;
import com.solactive.stocktracker.enums.ImportSource;
import com.solactive.stocktracker.enums.RejectReason;
import com.solactive.stocktracker.enums.ImportStatus;
import com.solactive.stocktracker.exception.ContentInProgressException;
import com.solactive.stocktracker.exception.InvalidFileException;
import com.solactive.stocktracker.ingest.HighLow;
import com.solactive.stocktracker.ingest.ParsedRow;
import com.solactive.stocktracker.ingest.PriceFileParser;
import com.solactive.stocktracker.repository.SummaryRepository;
import com.solactive.stocktracker.service.impl.PriceImportServiceImpl;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Unit tests of {@link PriceImportServiceImpl} with all collaborators mocked.
 */
@ExtendWith(MockitoExtension.class)
class PriceImportServiceImplTest {

    private static final ByteArrayResource FILE = new ByteArrayResource("csv".getBytes());
    private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");
    private static final long ID = 7L;

    @Mock
    private PriceFileParser parser;
    @Mock
    private ChecksumCalculator checksumCalculator;
    @Mock
    private FileImportTracker tracker;
    @Mock
    private SummaryRepository summaryRepository;

    private final ContentClaims contentClaims = new ContentClaims();
    private PriceImportServiceImpl service;

    @BeforeEach
    void setUp() {
        // A transaction manager mock makes TransactionTemplate simply run the callback.
        var transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));
        service = new PriceImportServiceImpl(parser, checksumCalculator, tracker, contentClaims, summaryRepository,
                transactionTemplate, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static FileImportResponse record(ImportSource source, ImportStatus status) {
        return new FileImportResponse(ID, "f.csv", source, status, "abc", 0, 0, 0, null,
                Instant.EPOCH, Instant.EPOCH, 0L, List.of());
    }

    @SuppressWarnings("unchecked")
    private void givenParsedRows(ParsedRow... rows) {
        doAnswer(inv -> {
            Consumer<ParsedRow> sink = inv.getArgument(2);
            for (ParsedRow row : rows) {
                sink.accept(row);
            }
            return null;
        }).when(parser).parse(eq("f.csv"), any(), any(Consumer.class));
    }

    @SuppressWarnings("unchecked")
    private void givenUnusableFile() {
        doThrow(InvalidFileException.missingHeader("f.csv", "price"))
                .when(parser).parse(eq("f.csv"), any(), any(Consumer.class));
    }

    /**
     * Folder files: every outcome is recorded.
     */
    @Nested
    class FolderFile {

        private void givenNewFile() {
            when(tracker.start("f.csv", ImportSource.FOLDER)).thenReturn(ID);
            when(tracker.isAlreadyProcessed("abc")).thenReturn(false);
        }

        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("a usable file: results merged and the record completed with counts and the error summary")
        void completesFile() {
            givenNewFile();
            givenParsedRows(
                    new ParsedRow.Accepted(2, tick("AAPL", "10", "2026-09-16T14:00:00Z")),
                    new ParsedRow.Rejected(3, RejectReason.PRICE_NOT_A_NUMBER, "bad price"),
                    new ParsedRow.Accepted(4, tick("AAPL", "12", "2026-09-16T14:00:01Z")),
                    new ParsedRow.Rejected(5, RejectReason.INVALID_TIMESTAMP, "bad timestamp"),
                    new ParsedRow.Rejected(6, RejectReason.INVALID_TIMESTAMP, "bad timestamp 2"));
            when(tracker.complete(eq(ID), eq("abc"), any()))
                    .thenReturn(record(ImportSource.FOLDER, ImportStatus.COMPLETED_WITH_ERRORS));

            FileImportResponse result = service.importFolderFile("f.csv", FILE, "abc");

            assertThat(result.status()).isEqualTo(ImportStatus.COMPLETED_WITH_ERRORS);
            ArgumentCaptor<ImportStats> stats = ArgumentCaptor.forClass(ImportStats.class);
            verify(tracker).complete(eq(ID), eq("abc"), stats.capture());
            assertThat(stats.getValue().totalRows()).isEqualTo(5);
            assertThat(stats.getValue().acceptedRows()).isEqualTo(2);
            assertThat(stats.getValue().rejectedRows()).isEqualTo(3);
            // Rejected rows are counted by reason, largest group first.
            assertThat(stats.getValue().errorSummary()).containsExactly(
                    new ErrorGroup(RejectReason.INVALID_TIMESTAMP, 2, 5, "bad timestamp"),
                    new ErrorGroup(RejectReason.PRICE_NOT_A_NUMBER, 1, 3, "bad price"));

            ArgumentCaptor<Collection<HighLow>> merged = ArgumentCaptor.forClass(Collection.class);
            verify(summaryRepository).mergeAll(merged.capture());
            assertThat(merged.getValue()).singleElement()
                    .satisfies(aapl -> assertThat(aapl.highPrice()).isEqualByComparingTo("12"));
        }

        @Test
        @DisplayName("already processed content: recorded as duplicate, not parsed, nothing merged")
        void skipsDuplicate() {
            when(tracker.start("f.csv", ImportSource.FOLDER)).thenReturn(ID);
            when(tracker.isAlreadyProcessed("abc")).thenReturn(true);
            when(tracker.skipDuplicate(ID, "abc")).thenReturn(record(ImportSource.FOLDER, ImportStatus.SKIPPED_DUPLICATE));

            assertThat(service.importFolderFile("f.csv", FILE, "abc").status()).isEqualTo(ImportStatus.SKIPPED_DUPLICATE);
            verifyNoInteractions(parser, summaryRepository);
        }

        @Test
        @DisplayName("an unusable file: recorded as FAILED with the reason; nothing merged; no exception")
        void failsUnusableFile() {
            givenNewFile();
            givenUnusableFile();
            when(tracker.fail(eq(ID), eq("abc"), any(),
                    eq("The file 'f.csv' is missing required header column(s): price")))
                    .thenReturn(record(ImportSource.FOLDER, ImportStatus.FAILED));

            assertThat(service.importFolderFile("f.csv", FILE, "abc").status()).isEqualTo(ImportStatus.FAILED);
            verifyNoInteractions(summaryRepository);
            verify(tracker, never()).complete(anyLong(), any(), any());
        }

        @Test
        @DisplayName("same content being processed right now: 409 exception and no record at all")
        @SuppressWarnings("try")
        void contentInProgress() {

            try (ContentClaims.Claim ignored = contentClaims.claim("abc")) {
                assertThatThrownBy(() -> service.importFolderFile("f.csv", FILE, "abc"))
                        .isInstanceOf(ContentInProgressException.class);
            }
            verifyNoInteractions(tracker, parser, summaryRepository, checksumCalculator);
        }

        @Test
        @DisplayName("a technical problem (e.g. database down): marked ERROR if possible, then rethrown")
        void technicalProblemIsRethrown() {
            givenNewFile();
            givenParsedRows(new ParsedRow.Accepted(2, tick("AAPL", "10", "2026-09-16T14:00:00Z")));
            doThrow(new DataAccessResourceFailureException("db down")).when(summaryRepository).mergeAll(any());

            assertThatThrownBy(() -> service.importFolderFile("f.csv", FILE, "abc"))
                    .isInstanceOf(DataAccessResourceFailureException.class);
            verify(tracker).error(eq(ID), eq("abc"), any(), eq("Unexpected error: db down"));
            verify(tracker, never()).complete(anyLong(), any(), any());
        }
    }

    /**
     * Uploads: only usable files are recorded.
     */
    @Nested
    class Upload {

        @Test
        @DisplayName("a usable upload: results merged and a completed record created with the start time")
        void recordsUsableUpload() {
            when(checksumCalculator.sha256(eq("f.csv"), any())).thenReturn("abc");
            when(tracker.isAlreadyProcessed("abc")).thenReturn(false);
            givenParsedRows(new ParsedRow.Accepted(2, tick("AAPL", "10", "2026-09-16T14:00:00Z")));
            when(tracker.recordUploadCompleted(eq("f.csv"), eq("abc"), eq(NOW), any()))
                    .thenReturn(record(ImportSource.UPLOAD, ImportStatus.COMPLETED));

            assertThat(service.importUpload("f.csv", FILE).status()).isEqualTo(ImportStatus.COMPLETED);
            verify(summaryRepository).mergeAll(any());
            verify(tracker, never()).start(any(), any());
        }

        @Test
        @DisplayName("an unusable upload (e.g. missing header columns): exception, and nothing is recorded")
        void unusableUploadLeavesNoRecord() {
            when(checksumCalculator.sha256(eq("f.csv"), any())).thenReturn("abc");
            when(tracker.isAlreadyProcessed("abc")).thenReturn(false);
            givenUnusableFile();

            assertThatThrownBy(() -> service.importUpload("f.csv", FILE))
                    .isInstanceOf(InvalidFileException.class)
                    .hasMessage("The file 'f.csv' is missing required header column(s): price");
            verify(tracker).isAlreadyProcessed("abc");
            verifyNoMoreInteractions(tracker);   // no start, no fail, no record of any kind
            verifyNoInteractions(summaryRepository);
        }

        @Test
        @DisplayName("same content being processed right now: 409 exception and nothing recorded")
        @SuppressWarnings("try")
        void uploadContentInProgress() {
            when(checksumCalculator.sha256(eq("f.csv"), any())).thenReturn("abc");

            try (ContentClaims.Claim ignored = contentClaims.claim("abc")) {
                assertThatThrownBy(() -> service.importUpload("f.csv", FILE))
                        .isInstanceOf(ContentInProgressException.class);
            }
            verifyNoInteractions(tracker, parser, summaryRepository);
        }

    }
}
