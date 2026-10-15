package com.solactive.stocktracker.controller;

import com.solactive.stocktracker.dto.FolderScanResponse;
import com.solactive.stocktracker.exception.ResourceNotFoundException;
import com.solactive.stocktracker.service.FolderScanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Starts the processing of the CSV files in the configured folder, and reports its progress.
 *
 * <p>An operations (admin) task: an admin or a scheduled job places any number of files in the
 * server folder and processes them with one call. Users send their own files through the upload
 * endpoint instead. There is no authentication in this version; in production this endpoint would
 * be restricted to an admin role.</p>
 */
@RestController
@RequestMapping(ApiPaths.FOLDER_SCANS)
@RequiredArgsConstructor
@Tag(name = "Folder scans", description = "Admin task: process the CSV files in the configured server folder")
public class FolderScanController {

    private final FolderScanService folderScanService;

    /**
     * Starts processing the {@code *.csv} files in the folder in the background and returns at once.
     * Files whose content was already handled are skipped without a new record. The folder is
     * configured on the server; the client cannot choose a path.
     *
     * @return 202 with the started scan and a {@code Location} header pointing at its progress
     */
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Start processing the CSV files in the folder",
            description = "Returns at once; the files are processed in the background, one after another in "
                    + "file-name order, and stay in the folder. Each file's status, row counts and problems are "
                    + "stored; files that could not be used (e.g. wrong header) are listed under failed. Files whose exact content was already processed, or already failed as "
                    + "unusable, are listed as alreadyProcessed and get no new record. Follow the progress with "
                    + "GET /api/v1/folder-scans/latest.")
    @ApiResponse(responseCode = "202", description = "Scan started; filesFound tells how many files it will handle")
    @ApiResponse(responseCode = "409", description = "A scan is already running; follow it with "
            + "GET /api/v1/folder-scans/latest",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "The folder cannot be accessed; no scan is started",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<FolderScanResponse> start() {
        FolderScanResponse started = folderScanService.start();
        URI progress = ServletUriComponentsBuilder.fromCurrentRequest().path("/latest").build().toUri();
        return ResponseEntity.accepted().location(progress).body(started);
    }

    /**
     * Returns the progress of the running scan, or the result of the last finished one.
     *
     * @return the latest scan
     */
    @GetMapping(path = "/latest", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Progress of the running scan, or the result of the last one",
            description = "Kept in memory: only the latest scan since the application started. The result of "
                    + "every processed file is also stored and listed by GET /api/v1/imports.")
    @ApiResponse(responseCode = "200", description = "The latest scan (status RUNNING, COMPLETED or FAILED)")
    @ApiResponse(responseCode = "404", description = "No scan has run since the application started",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public FolderScanResponse latest() {
        return folderScanService.latest().orElseThrow(ResourceNotFoundException::folderScan);
    }
}
