package com.solactive.stocktracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.solactive.stocktracker.dto.FileImportResponse;
import com.solactive.stocktracker.dto.FolderScanResponse;
import com.solactive.stocktracker.enums.ImportSource;
import com.solactive.stocktracker.enums.ImportStatus;
import com.solactive.stocktracker.enums.ScanStatus;
import com.solactive.stocktracker.exception.ContentInProgressException;
import com.solactive.stocktracker.exception.FolderAccessException;
import com.solactive.stocktracker.exception.InvalidFileException;
import com.solactive.stocktracker.exception.ScanInProgressException;
import com.solactive.stocktracker.service.impl.FolderScanServiceImpl;
import com.solactive.stocktracker.support.TestFixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.task.TaskExecutor;

/**
 * Unit tests of {@link FolderScanServiceImpl} using a real temporary folder, a real checksum
 * calculator, and mocked import service and tracker.
 */
@ExtendWith(MockitoExtension.class)
class FolderScanServiceImplTest {

    @TempDir
    private Path root;
    @Mock
    private PriceImportService importService;
    @Mock
    private FileImportTracker tracker;

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC);
    /** Runs the background task right away, so a scan has finished when start() returns. */
    private static final TaskExecutor RUN_NOW = Runnable::run;

    private final ChecksumCalculator checksumCalculator = new ChecksumCalculator();
    private Path folder;
    private FolderScanServiceImpl service;

    @BeforeEach
    void setUp() {
        folder = root.resolve("input");
        service = newService(checksumCalculator, RUN_NOW);
    }

    private FolderScanServiceImpl newService(ChecksumCalculator calculator, TaskExecutor executor) {
        return new FolderScanServiceImpl(importService, calculator, tracker, TestFixtures.properties(folder),
                executor, CLOCK);
    }

    /** Starts a scan and returns its result (the executor runs it before start() returns). */
    private FolderScanResponse scan() {
        assertThat(service.start().status()).isEqualTo(ScanStatus.RUNNING);
        return service.latest().orElseThrow();
    }

    private static FileImportResponse record(long id, String name, ImportStatus status) {
        return new FileImportResponse(id, name, ImportSource.FOLDER, status, null, 0, 0, 0, null,
                Instant.EPOCH, Instant.EPOCH, 0L, List.of());
    }

    private void givenFiles(String... names) throws IOException {
        Files.createDirectories(folder);
        for (String name : names) {
            Files.writeString(folder.resolve(name), "content of " + name);
        }
    }

    private String checksumOf(String name) {
        return checksumCalculator.sha256(name, new ByteArrayResource(("content of " + name).getBytes()));
    }

    @Test
    @DisplayName("new CSV files are processed one after another in name order; other files are ignored")
    void processesInNameOrder() throws IOException {
        givenFiles("b.csv", "a.CSV", "notes.txt");
        Files.createDirectories(folder.resolve("sub.csv"));   // a folder, not a file
        when(tracker.isAlreadyHandled(anyString())).thenReturn(false);
        when(importService.importFolderFile(eq("a.CSV"), any(), anyString()))
                .thenReturn(record(1, "a.CSV", ImportStatus.COMPLETED));
        when(importService.importFolderFile(eq("b.csv"), any(), anyString()))
                .thenReturn(record(2, "b.csv", ImportStatus.COMPLETED));

        FolderScanResponse response = scan();

        InOrder order = inOrder(importService);
        order.verify(importService).importFolderFile(eq("a.CSV"), any(), anyString());
        order.verify(importService).importFolderFile(eq("b.csv"), any(), anyString());
        assertThat(response.status()).isEqualTo(ScanStatus.COMPLETED);
        assertThat(response.filesFound()).isEqualTo(2);
        assertThat(response.filesDone()).isEqualTo(2);
        assertThat(response.processed()).extracting(FileImportResponse::id).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("files stay in the folder after processing")
    void filesAreNotMoved() throws IOException {
        givenFiles("a.csv");
        when(tracker.isAlreadyHandled(anyString())).thenReturn(false);
        when(importService.importFolderFile(eq("a.csv"), any(), anyString())).thenReturn(record(1, "a.csv", ImportStatus.FAILED));

        scan();

        assertThat(folder.resolve("a.csv")).hasContent("content of a.csv");
        try (var entries = Files.list(folder)) {
            assertThat(entries).hasSize(1);
        }
    }

    @Test
    @DisplayName("already handled content is skipped without calling the import (so no new record)")
    void skipsAlreadyHandledFiles() throws IOException {
        givenFiles("done.csv", "new.csv");
        when(tracker.isAlreadyHandled(checksumOf("done.csv"))).thenReturn(true);
        when(tracker.isAlreadyHandled(checksumOf("new.csv"))).thenReturn(false);
        when(importService.importFolderFile(eq("new.csv"), any(), anyString())).thenReturn(record(5, "new.csv", ImportStatus.COMPLETED));

        FolderScanResponse response = scan();

        assertThat(response.filesFound()).isEqualTo(2);
        assertThat(response.alreadyProcessed()).containsExactly("done.csv");
        assertThat(response.processed()).extracting(FileImportResponse::fileName).containsExactly("new.csv");
        verify(importService, never()).importFolderFile(eq("done.csv"), any(), anyString());
    }

    @Test
    @DisplayName("the checksum is computed once and passed on; an unreadable file is recorded as FAILED without being imported")
    void checksumPassedOnAndUnreadableRecorded() throws IOException {
        givenFiles("good.csv", "locked.csv");
        ChecksumCalculator calculator = spy(new ChecksumCalculator());
        // lenient: the real method is meant to run for the other file
        lenient().doThrow(InvalidFileException.unreadable("locked.csv", "permission denied"))
                .when(calculator).sha256(eq("locked.csv"), any());
        service = newService(calculator, RUN_NOW);
        when(tracker.isAlreadyHandled(checksumOf("good.csv"))).thenReturn(false);
        when(importService.importFolderFile(eq("good.csv"), any(), eq(checksumOf("good.csv"))))
                .thenReturn(record(1, "good.csv", ImportStatus.COMPLETED));
        when(tracker.start("locked.csv", ImportSource.FOLDER)).thenReturn(2L);
        when(tracker.fail(eq(2L), isNull(), any(), eq("The file 'locked.csv' could not be read: permission denied")))
                .thenReturn(record(2, "locked.csv", ImportStatus.FAILED));

        FolderScanResponse response = scan();

        assertThat(response.processed()).extracting(FileImportResponse::fileName).containsExactly("good.csv");
        assertThat(response.failed()).extracting(FileImportResponse::fileName).containsExactly("locked.csv");
        assertThat(response.filesDone()).isEqualTo(2);
        verify(importService, never()).importFolderFile(eq("locked.csv"), any(), any());
        verify(calculator).sha256(eq("good.csv"), any());   // exactly once
    }

    @Test
    @DisplayName("a file whose content is being processed right now is listed as in progress, without failing the scan")
    void contentInProgressIsListed() throws IOException {
        givenFiles("busy.csv", "free.csv");
        when(tracker.isAlreadyHandled(anyString())).thenReturn(false);
        when(importService.importFolderFile(eq("busy.csv"), any(), anyString())).thenThrow(new ContentInProgressException());
        when(importService.importFolderFile(eq("free.csv"), any(), anyString())).thenReturn(record(1, "free.csv", ImportStatus.COMPLETED));

        FolderScanResponse response = scan();

        assertThat(response.inProgress()).containsExactly("busy.csv");
        assertThat(response.processed()).extracting(FileImportResponse::fileName).containsExactly("free.csv");
    }

    @Test
    @DisplayName("start returns at once; a second start while the scan runs -> ScanInProgressException")
    void onlyOneScanAtATime() throws IOException {
        givenFiles("a.csv");
        when(tracker.isAlreadyHandled(anyString())).thenReturn(false);
        when(importService.importFolderFile(eq("a.csv"), any(), anyString())).thenReturn(record(1, "a.csv", ImportStatus.COMPLETED));
        List<Runnable> background = new ArrayList<>();
        service = newService(checksumCalculator, background::add);   // keeps the scan waiting

        FolderScanResponse started = service.start();

        assertThat(started.status()).isEqualTo(ScanStatus.RUNNING);
        assertThat(started.filesFound()).isEqualTo(1);   // known before the answer
        assertThat(service.latest()).contains(started);
        verifyNoInteractions(importService);   // nothing processed yet: the request did not wait
        assertThatThrownBy(() -> service.start()).isInstanceOf(ScanInProgressException.class);

        background.forEach(Runnable::run);   // the scan finishes
        assertThat(service.latest().orElseThrow().status()).isEqualTo(ScanStatus.COMPLETED);
        assertThat(service.start().status()).isEqualTo(ScanStatus.RUNNING);   // a new scan can start
    }

    @Test
    @DisplayName("a folder that cannot be used is reported at once (FolderAccessException) and no scan starts")
    void unusableFolderIsReportedAtOnce() throws IOException {
        Files.writeString(root.resolve("not-a-folder"), "x");
        folder = root.resolve("not-a-folder").resolve("input");
        service = newService(checksumCalculator, RUN_NOW);

        assertThatThrownBy(() -> service.start()).isInstanceOf(FolderAccessException.class);
        assertThat(service.latest()).isEmpty();
        verifyNoInteractions(importService, tracker);
    }
}
