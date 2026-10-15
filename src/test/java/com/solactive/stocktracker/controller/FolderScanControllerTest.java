package com.solactive.stocktracker.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.solactive.stocktracker.dto.FileImportResponse;
import com.solactive.stocktracker.dto.FolderScanResponse;
import com.solactive.stocktracker.enums.ImportSource;
import com.solactive.stocktracker.enums.ImportStatus;
import com.solactive.stocktracker.enums.ScanStatus;
import com.solactive.stocktracker.exception.ScanInProgressException;
import com.solactive.stocktracker.service.FolderScanService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer tests of {@link FolderScanController}.
 */
@WebMvcTest(FolderScanController.class)
class FolderScanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FolderScanService folderScanService;

    @Test
    @DisplayName("POST starts a scan in the background -> 202 with a Location to follow its progress")
    void startsScan() throws Exception {
        when(folderScanService.start()).thenReturn(FolderScanResponse.started("/data/input", Instant.EPOCH));

        mockMvc.perform(post(ApiPaths.FOLDER_SCANS))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", endsWith("/api/v1/folder-scans/latest")))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.filesDone").value(0));
    }

    @Test
    @DisplayName("POST while a scan is running -> 409 pointing at its progress")
    void conflict() throws Exception {
        when(folderScanService.start()).thenThrow(new ScanInProgressException());

        mockMvc.perform(post(ApiPaths.FOLDER_SCANS))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("GET /api/v1/folder-scans/latest")));
    }

    @Test
    @DisplayName("GET latest returns the scan's progress or result, unusable files under failed; 404 before the first scan")
    void latest() throws Exception {
        FileImportResponse record = new FileImportResponse(1L, "a.csv", ImportSource.FOLDER, ImportStatus.COMPLETED,
                "abc", 10, 10, 0, null, Instant.EPOCH, Instant.EPOCH, 0L, List.of());
        FileImportResponse broken = new FileImportResponse(2L, "b.csv", ImportSource.FOLDER, ImportStatus.FAILED,
                "def", 0, 0, 0, "missing header", Instant.EPOCH, Instant.EPOCH, 0L, List.of());
        FolderScanResponse done = FolderScanResponse.started("/data/input", Instant.EPOCH)
                .withFilesFound(3).withRecord(record).withRecord(broken).withAlreadyProcessed("old.csv")
                .finished(ScanStatus.COMPLETED, null, Instant.EPOCH);
        when(folderScanService.latest()).thenReturn(Optional.of(done));

        mockMvc.perform(get(ApiPaths.FOLDER_SCANS + "/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.filesFound").value(3))
                .andExpect(jsonPath("$.filesDone").value(3))
                .andExpect(jsonPath("$.processed.length()").value(1))
                .andExpect(jsonPath("$.processed[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.failed[0].fileName").value("b.csv"))
                .andExpect(jsonPath("$.alreadyProcessed[0]").value("old.csv"));

        when(folderScanService.latest()).thenReturn(Optional.empty());
        mockMvc.perform(get(ApiPaths.FOLDER_SCANS + "/latest")).andExpect(status().isNotFound());
    }
}
