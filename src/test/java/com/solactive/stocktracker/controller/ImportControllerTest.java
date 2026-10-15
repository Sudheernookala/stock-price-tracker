package com.solactive.stocktracker.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.solactive.stocktracker.dto.FileImportResponse;
import com.solactive.stocktracker.dto.ErrorGroup;
import com.solactive.stocktracker.enums.ImportSource;
import com.solactive.stocktracker.enums.ImportStatus;
import com.solactive.stocktracker.enums.RejectReason;
import com.solactive.stocktracker.exception.ContentInProgressException;
import com.solactive.stocktracker.exception.InvalidFileException;
import com.solactive.stocktracker.service.FileImportQueryService;
import com.solactive.stocktracker.service.PriceImportService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer tests of {@link ImportController}: status codes, headers and error bodies.
 */
@WebMvcTest(ImportController.class)
class ImportControllerTest {

    private static final MockMultipartFile CSV = new MockMultipartFile("file", "prices.csv", "text/csv",
            "stockSymbol,price,timestamp,exchange\nAAPL,1,2026-09-16T14:00:00Z,NYSE\n".getBytes());

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PriceImportService importService;
    @MockitoBean   // required by the controller; not used by the remaining tests
    private FileImportQueryService queryService;

    private static FileImportResponse record(long id, ImportStatus status, String message, List<ErrorGroup> errorSummary) {
        return new FileImportResponse(id, "prices.csv", ImportSource.UPLOAD, status, "abc", 2, 1, 1, message,
                Instant.parse("2026-10-13T10:00:00Z"), Instant.parse("2026-10-13T10:00:01Z"), 1000L, errorSummary);
    }

    @Test
    @DisplayName("POST returns 201 with Location header and the processing record")
    void importsFile() throws Exception {
        when(importService.importUpload(eq("prices.csv"), any())).thenReturn(
                record(12, ImportStatus.COMPLETED_WITH_ERRORS, null, List.of(new ErrorGroup(RejectReason.PRICE_NOT_A_NUMBER, 1, 3, "bad price"))));

        mockMvc.perform(multipart(ApiPaths.IMPORTS).file(CSV))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/imports/12")))
                .andExpect(jsonPath("$.status").value("COMPLETED_WITH_ERRORS"))
                .andExpect(jsonPath("$.source").value("UPLOAD"))
                .andExpect(jsonPath("$.errorSummary[0].code").value("PRICE_NOT_A_NUMBER"))
                .andExpect(jsonPath("$.durationMs").value(1000));
    }

    @Test
    @DisplayName("POST of an unusable file (e.g. wrong header) -> 400 with the reason; no record id")
    void unusableFile() throws Exception {
        when(importService.importUpload(eq("prices.csv"), any()))
                .thenThrow(InvalidFileException.missingHeader("prices.csv", "price"));

        mockMvc.perform(multipart(ApiPaths.IMPORTS).file(CSV))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("The file 'prices.csv' is missing required header column(s): price"))
                .andExpect(jsonPath("$.importId").doesNotExist());
    }

    @Test
    @DisplayName("POST while the same content is being processed -> 409")
    void contentInProgress() throws Exception {
        when(importService.importUpload(eq("prices.csv"), any())).thenThrow(new ContentInProgressException());

        mockMvc.perform(multipart(ApiPaths.IMPORTS).file(CSV))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value(
                        "A file with the same content is already being processed. Try again when it has finished."));
    }

    @Test
    @DisplayName("POST of a file that is not .csv -> 400 before anything is read or recorded")
    void notCsv() throws Exception {
        mockMvc.perform(multipart(ApiPaths.IMPORTS).file(
                        new MockMultipartFile("file", "prices.xlsx", "application/vnd.ms-excel", "x".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Only CSV files are supported; 'prices.xlsx' is not a .csv file."));
        verifyNoInteractions(importService);
    }

    @Test
    @DisplayName("unexpected failure -> 500 without internal details")
    void unexpectedError() throws Exception {
        when(importService.importUpload(eq("prices.csv"), any())).thenThrow(new IllegalStateException("secret detail"));

        mockMvc.perform(multipart(ApiPaths.IMPORTS).file(CSV))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value(startsWith("An unexpected error occurred. Reference id: ")))
                .andExpect(jsonPath("$.detail").value(not(containsString("secret detail"))));
    }
}
