package com.solactive.stocktracker.controller;

import com.solactive.stocktracker.dto.FileImportResponse;
import com.solactive.stocktracker.exception.InvalidFileException;
import com.solactive.stocktracker.service.FileImportQueryService;
import com.solactive.stocktracker.service.PriceImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * File uploads and the stored processing records of all files (from the folder or uploaded).
 */
@RestController
@RequestMapping(ApiPaths.IMPORTS)
@RequiredArgsConstructor
@Tag(name = "Imports", description = "Upload CSV files and look up the processing status of every file")
public class ImportController {

    private static final int MAX_LIMIT = 500;

    private static final String CSV_EXTENSION = ".csv";

    private final PriceImportService importService;
    private final FileImportQueryService queryService;

    /**
     * Processes a CSV file uploaded as {@code multipart/form-data}.
     *
     * @param file the CSV file (part name {@code file})
     * @return 201 with the processing record and a {@code Location} header pointing at it
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Upload and process a CSV price file",
            description = "Valid rows are merged into the daily high/low summaries; invalid rows are skipped "
                    + "and counted by reason in the record. A file with already processed content is recorded as "
                    + "SKIPPED_DUPLICATE. A file that is not a CSV, or is unusable as a whole, is rejected "
                    + "with 400 and nothing is recorded.")
    @ApiResponse(responseCode = "201", description = "File processed (status COMPLETED, "
            + "COMPLETED_WITH_ERRORS or SKIPPED_DUPLICATE)")
    @ApiResponse(responseCode = "400", description = "Missing upload, not a .csv file, empty file, missing "
            + "header columns or broken CSV. Nothing is recorded.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "A file with the same content is being processed right now. "
            + "Nothing is recorded; try again later.",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "413", description = "File too large",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<FileImportResponse> importFile(
            @Parameter(description = "CSV file with header stockSymbol,price,timestamp,exchange[,volume]")
            @RequestPart("file") MultipartFile file) {
        String fileName = Objects.requireNonNullElse(file.getOriginalFilename(), "upload.csv");
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(CSV_EXTENSION)) {
            throw InvalidFileException.notCsv(fileName);
        }
        if (file.isEmpty()) {
            throw InvalidFileException.empty(fileName);
        }
        FileImportResponse result = importService.importUpload(fileName, file);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(result.id()).toUri();
        return ResponseEntity.created(location).body(result);
    }

    /**
     * Lists the most recent processing records of all files.
     *
     * @param limit maximum number of records (1..{@value #MAX_LIMIT})
     * @return records, newest first, without their error summary
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List the most recent file processing records")
    @ApiResponse(responseCode = "200", description = "Records, newest first")
    @ApiResponse(responseCode = "400", description = "Invalid limit",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public List<FileImportResponse> findRecent(
            @Parameter(description = "Maximum number of records (1-500)")
            @RequestParam(defaultValue = "50")
            @Min(value = 1, message = "limit must be greater than or equal to 1")
            @Max(value = MAX_LIMIT, message = "limit must be less than or equal to 500") int limit) {
        return queryService.findRecent(limit);
    }

    /**
     * Returns one processing record including its error summary.
     *
     * @param id record id
     * @return the record
     */
    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get one file processing record, including its error summary")
    @ApiResponse(responseCode = "200", description = "Record found")
    @ApiResponse(responseCode = "404", description = "Unknown id",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public FileImportResponse findById(@PathVariable long id) {
        return queryService.findById(id);
    }
}
