package com.solactive.stocktracker.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.solactive.stocktracker.dto.StockSummaryResponse;
import com.solactive.stocktracker.service.StockSummaryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer tests of {@link SummaryController}, including the exact JSON shape of the spec.
 */
@WebMvcTest(SummaryController.class)
class SummaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StockSummaryService summaryService;

    @Test
    @DisplayName("GET /summaries returns a plain array in the specification's format (6-decimal prices, ISO instants)")
    void returnsSpecFormat() throws Exception {
        when(summaryService.getDailySummaries(null)).thenReturn(List.of(new StockSummaryResponse("MSFT",
                new BigDecimal("420.000000"), Instant.parse("2026-09-16T12:33:23.902691Z"),
                new BigDecimal("380.010004"), Instant.parse("2026-09-16T12:50:42.902691Z"))));

        mockMvc.perform(get(ApiPaths.SUMMARIES))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"stockSymbol":"MSFT",
                          "highPrice":420.000000,"highTimestamp":"2026-09-16T12:33:23.902691Z",
                          "lowPrice":380.010004,"lowTimestamp":"2026-09-16T12:50:42.902691Z"}]
                        """, JsonCompareMode.STRICT))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"highPrice\":420.000000")));
    }

    @Test
    @DisplayName("malformed symbol -> 400 with validation message")
    void invalidSymbol() throws Exception {
        mockMvc.perform(get(ApiPaths.SUMMARIES + "/BAD$SYMBOL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("symbol must be 1-10 characters: letters, digits, '.' or '-'"));
        verifyNoInteractions(summaryService);
    }

}
