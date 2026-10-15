package com.solactive.stocktracker.dto;

import com.solactive.stocktracker.entity.FileImport;
import com.solactive.stocktracker.enums.ImportSource;
import com.solactive.stocktracker.enums.ImportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Processing record of one file.
 *
 * @param id           record id
 * @param fileName     file name
 * @param source       how the file reached the application
 * @param status       processing status
 * @param checksum     SHA-256 of the content ({@code null} if unreadable)
 * @param totalRows    data rows read
 * @param acceptedRows rows used in the high/low calculation
 * @param rejectedRows rows rejected by validation
 * @param errorMessage file-level problem, if any
 * @param startedAt    when processing started
 * @param completedAt  when processing finished ({@code null} while processing)
 * @param durationMs   processing time in milliseconds ({@code null} while processing)
 * @param errorSummary every rejected row counted by reason; empty in list responses
 */
@Schema(description = "Processing record of one file")
public record FileImportResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "stock-prices.csv") String fileName,
        ImportSource source,
        ImportStatus status,
        String checksum,
        @Schema(example = "107") long totalRows,
        @Schema(example = "102") long acceptedRows,
        @Schema(example = "5") long rejectedRows,
        @Schema(nullable = true) String errorMessage,
        Instant startedAt,
        @Schema(nullable = true) Instant completedAt,
        @Schema(nullable = true, example = "45") Long durationMs,
        List<ErrorGroup> errorSummary) {

    /**
     * Defensive copy so the record stays immutable.
     */
    public FileImportResponse {
        errorSummary = errorSummary == null ? List.of() : List.copyOf(errorSummary);
    }

    /**
     * Maps an entity. Must be called inside a transaction when {@code includeErrors} is true,
     * because the error summary is loaded lazily.
     *
     * @param entity        processing record
     * @param includeErrors whether to include the error summary
     * @return API representation
     */
    public static FileImportResponse from(FileImport entity, boolean includeErrors) {
        Long durationMs = entity.getCompletedAt() == null ? null
                : Duration.between(entity.getStartedAt(), entity.getCompletedAt()).toMillis();
        List<ErrorGroup> errorSummary = includeErrors
                ? entity.getErrorSummary().stream()
                        .map(g -> new ErrorGroup(g.getCode(), g.getRowCount(), g.getFirstLine(), g.getExample())).toList()
                : List.of();
        return new FileImportResponse(entity.getId(), entity.getFileName(), entity.getSource(), entity.getStatus(),
                entity.getChecksum(), entity.getTotalRows(), entity.getAcceptedRows(), entity.getRejectedRows(),
                entity.getErrorMessage(), entity.getStartedAt(), entity.getCompletedAt(), durationMs, errorSummary);
    }
}
