package com.solactive.stocktracker.controller;

import com.solactive.stocktracker.common.Symbols;
import com.solactive.stocktracker.dto.DailySummaryResponse;
import com.solactive.stocktracker.dto.StockSummaryResponse;
import com.solactive.stocktracker.service.StockSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Daily high/low summaries.
 *
 * <p>The list endpoint returns a plain JSON array, exactly the shape required by the
 * specification. Its size is bounded by the number of symbols, not by the number of rows.</p>
 */
@RestController
@RequestMapping(ApiPaths.SUMMARIES)
@RequiredArgsConstructor
@Tag(name = "Summaries", description = "Daily high/low per stock symbol")
public class SummaryController {

    private final StockSummaryService summaryService;

    /**
     * Lists the high/low of every symbol for one trading day.
     *
     * @param date trading day (ISO yyyy-MM-dd); defaults to the latest day with data
     * @return summaries ordered by symbol (empty array when no data)
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "High/low of every symbol for a trading day",
            description = "Without a date, the most recent trading day of any symbol is used; a symbol "
                    + "without data on that day is not listed. Use ?date= or the history endpoint for other days.")
    @ApiResponse(responseCode = "200", description = "Summaries (possibly empty)")
    @ApiResponse(responseCode = "400", description = "Invalid date",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public List<StockSummaryResponse> getDailySummaries(
            @Parameter(description = "Trading day (yyyy-MM-dd); defaults to the most recent day", example = "2026-09-16")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return summaryService.getDailySummaries(date);
    }

    /**
     * Returns the high/low of one symbol for one trading day.
     *
     * @param symbol ticker symbol (case-insensitive)
     * @param date   trading day; defaults to the latest day with data
     * @return the summary
     */
    @GetMapping(path = "/{symbol}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "High/low of one symbol for a trading day",
            description = "Without a date, the most recent trading day of this symbol is used.")
    @ApiResponse(responseCode = "200", description = "Summary found")
    @ApiResponse(responseCode = "400", description = "Invalid symbol or date",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No data for the symbol/day",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public StockSummaryResponse getSymbolSummary(
            @Parameter(description = "Ticker symbol", example = "AAPL")
            @PathVariable @Pattern(regexp = Symbols.REQUEST_REGEX, message = Symbols.REQUEST_MESSAGE) String symbol,
            @Parameter(description = "Trading day (yyyy-MM-dd); defaults to the symbol's most recent day",
                    example = "2026-09-16")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return summaryService.getSymbolSummary(symbol, date);
    }

    /**
     * Returns the high/low of one symbol for every trading day with data.
     *
     * @param symbol ticker symbol (case-insensitive)
     * @return one entry per trading day, oldest first
     */
    @GetMapping(path = "/{symbol}/history", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "High/low of one symbol for every trading day, oldest first")
    @ApiResponse(responseCode = "200", description = "One entry per trading day")
    @ApiResponse(responseCode = "400", description = "Invalid symbol",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No data for the symbol",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public List<DailySummaryResponse> getSymbolHistory(
            @Parameter(description = "Ticker symbol", example = "AAPL")
            @PathVariable @Pattern(regexp = Symbols.REQUEST_REGEX, message = Symbols.REQUEST_MESSAGE)
            String symbol) {
        return summaryService.getSymbolHistory(symbol);
    }
}
