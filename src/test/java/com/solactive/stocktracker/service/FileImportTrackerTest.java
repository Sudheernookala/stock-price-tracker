package com.solactive.stocktracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.solactive.stocktracker.dto.ErrorGroup;
import com.solactive.stocktracker.dto.FileImportResponse;
import com.solactive.stocktracker.dto.ImportStats;
import com.solactive.stocktracker.entity.FileImport;
import com.solactive.stocktracker.enums.ImportSource;
import com.solactive.stocktracker.enums.RejectReason;
import com.solactive.stocktracker.enums.ImportStatus;
import com.solactive.stocktracker.repository.FileImportRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests of {@link FileImportTracker} with the repository mocked.
 */
@ExtendWith(MockitoExtension.class)
class FileImportTrackerTest {

    private static final Instant START = Instant.parse("2026-10-13T10:00:00Z");

    @Mock
    private FileImportRepository repository;

    private FileImport stored;
    private FileImportTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new FileImportTracker(repository, Clock.fixed(START.plusSeconds(2), ZoneOffset.UTC));
        stored = FileImport.builder().id(1L).fileName("f.csv").source(ImportSource.FOLDER)
                .status(ImportStatus.PROCESSING).startedAt(START).build();
    }

    private void givenStoredRecord() {
        when(repository.findById(1L)).thenReturn(Optional.of(stored));
        when(repository.save(any(FileImport.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("complete with rejected rows -> COMPLETED_WITH_ERRORS and the error summary is stored")
    void completeWithErrors() {
        givenStoredRecord();

        FileImportResponse result = tracker.complete(1L, "abc",
                new ImportStats(2, 1, 1, List.of(new ErrorGroup(RejectReason.PRICE_NOT_A_NUMBER, 1, 5, "Price 'N/A' is not a valid decimal number."))));

        assertThat(result.status()).isEqualTo(ImportStatus.COMPLETED_WITH_ERRORS);
        assertThat(result.errorSummary()).containsExactly(
                new ErrorGroup(RejectReason.PRICE_NOT_A_NUMBER, 1, 5, "Price 'N/A' is not a valid decimal number."));
        assertThat(stored.getErrorSummary()).singleElement().satisfies(e -> assertThat(e.getFileImport()).isSameAs(stored));
    }

    @Test
    @DisplayName("long values are cut to the column sizes")
    void truncatesLongValues() {
        givenStoredRecord();
        String longReason = "x".repeat(800);

        FileImportResponse result = tracker.fail(1L, null,
                new ImportStats(1, 0, 1, List.of(new ErrorGroup(RejectReason.INVALID_SYMBOL, 1, 2, longReason))), "y".repeat(3000));

        assertThat(result.status()).isEqualTo(ImportStatus.FAILED);
        assertThat(result.errorMessage()).hasSize(2000).endsWith("...");
        assertThat(result.errorSummary().getFirst().example()).hasSize(500);
    }

    @Test
    @DisplayName("already handled = completed or failed content; ERROR and duplicates are not enough")
    void alreadyHandledStatuses() {
        when(repository.existsByChecksumAndStatusIn(eq("abc"), any())).thenReturn(true);

        assertThat(tracker.isAlreadyHandled("abc")).isTrue();
        verify(repository).existsByChecksumAndStatusIn("abc", java.util.EnumSet.of(
                ImportStatus.COMPLETED, ImportStatus.COMPLETED_WITH_ERRORS, ImportStatus.FAILED));
    }

    @Test
    @DisplayName("upload records are created directly as completed (with errors) or duplicate, with the given start time")
    void uploadRecords() {
        when(repository.save(any(FileImport.class))).thenAnswer(inv -> inv.getArgument(0));

        FileImportResponse completed = tracker.recordUploadCompleted("u.csv", "abc", START,
                new ImportStats(2, 1, 1, List.of(new ErrorGroup(RejectReason.PRICE_NOT_A_NUMBER, 1, 3, "bad"))));
        FileImportResponse duplicate = tracker.recordUploadDuplicate("u.csv", "abc", START);

        assertThat(completed.source()).isEqualTo(ImportSource.UPLOAD);
        assertThat(completed.status()).isEqualTo(ImportStatus.COMPLETED_WITH_ERRORS);
        assertThat(completed.startedAt()).isEqualTo(START);
        assertThat(completed.durationMs()).isEqualTo(2000);
        assertThat(completed.errorSummary()).extracting(ErrorGroup::firstLine).containsExactly(3L);
        assertThat(duplicate.status()).isEqualTo(ImportStatus.SKIPPED_DUPLICATE);
        assertThat(duplicate.errorMessage()).contains("already processed");
    }

}
