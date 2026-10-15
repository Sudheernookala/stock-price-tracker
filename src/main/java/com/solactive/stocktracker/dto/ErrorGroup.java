package com.solactive.stocktracker.dto;

import com.solactive.stocktracker.enums.RejectReason;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * All rejected rows of a file that share one reason.
 *
 * @param code      why the rows were rejected
 * @param count     how many rows were rejected for this reason
 * @param firstLine line number of the first such row
 * @param example   message of the first such row
 */
@Schema(description = "Rejected rows of a file grouped by reason")
public record ErrorGroup(
        RejectReason code,
        @Schema(example = "2") long count,
        @Schema(example = "103") long firstLine,
        @Schema(example = "Required field 'price' is missing or blank.") String example) {
}
