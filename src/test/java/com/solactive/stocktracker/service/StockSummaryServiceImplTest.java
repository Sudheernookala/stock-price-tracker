package com.solactive.stocktracker.service;

import static com.solactive.stocktracker.support.TestFixtures.DAY;
import static com.solactive.stocktracker.support.TestFixtures.highLow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.solactive.stocktracker.dto.DailySummaryResponse;
import com.solactive.stocktracker.dto.StockSummaryResponse;
import com.solactive.stocktracker.exception.ResourceNotFoundException;
import com.solactive.stocktracker.ingest.HighLow;
import com.solactive.stocktracker.repository.SummaryRepository;
import com.solactive.stocktracker.service.impl.StockSummaryServiceImpl;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests of {@link StockSummaryServiceImpl} with the repository mocked.
 */
@ExtendWith(MockitoExtension.class)
class StockSummaryServiceImplTest {

    private static final LocalDate NEXT_DAY = DAY.plusDays(1);

    @Mock
    private SummaryRepository repository;
    @InjectMocks
    private StockSummaryServiceImpl service;

    private static HighLow onDay(String symbol, LocalDate day, String high, String low) {
        Instant time = day.atTime(14, 0).toInstant(java.time.ZoneOffset.UTC);
        return new HighLow(symbol, day, new BigDecimal(high).setScale(6), time, new BigDecimal(low).setScale(6), time);
    }

    @Test
    @DisplayName("list without a date: the latest day of any symbol, mapped to the spec format")
    void listUsesLatestDayOverall() {
        when(repository.findLatestTradingDate()).thenReturn(Optional.of(DAY));
        when(repository.findByTradingDate(DAY)).thenReturn(List.of(
                highLow("MSFT", "420.000001", "2026-09-16T12:33:23.902691Z", "380.010004", "2026-09-16T12:50:42.902691Z")));

        List<StockSummaryResponse> result = service.getDailySummaries(null);

        assertThat(result).containsExactly(new StockSummaryResponse("MSFT",
                new BigDecimal("420.000001"), Instant.parse("2026-09-16T12:33:23.902691Z"),
                new BigDecimal("380.010004"), Instant.parse("2026-09-16T12:50:42.902691Z")));
    }

    @Test
    @DisplayName("symbol without a date: that symbol's latest day, even if other symbols have newer days")
    void symbolUsesItsOwnLatestDay() {
        when(repository.findLatestTradingDate("MSFT")).thenReturn(Optional.of(DAY));
        when(repository.findBySymbolAndTradingDate("MSFT", DAY)).thenReturn(Optional.of(onDay("MSFT", DAY, "400", "390")));

        StockSummaryResponse result = service.getSymbolSummary(" msft ", null);

        assertThat(result.stockSymbol()).isEqualTo("MSFT");
        assertThat(result.highPrice()).isEqualByComparingTo("400");
        verify(repository, never()).findLatestTradingDate();   // the overall latest day is not used
    }

    @Test
    @DisplayName("symbol with no data at all -> 404")
    void unknownSymbol() {
        when(repository.findLatestTradingDate("XYZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSymbolSummary("XYZ", null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No high/low data found for symbol 'XYZ'.");
        verify(repository, never()).findBySymbolAndTradingDate(any(), any());
    }

    @Test
    @DisplayName("history: every day of the symbol, with the date in each entry, oldest first")
    void history() {
        when(repository.findBySymbol("AAPL")).thenReturn(List.of(
                onDay("AAPL", DAY, "155", "150"), onDay("AAPL", NEXT_DAY, "170", "165")));

        List<DailySummaryResponse> history = service.getSymbolHistory("aapl");

        assertThat(history).extracting(DailySummaryResponse::tradingDate).containsExactly(DAY, NEXT_DAY);
        assertThat(history.get(1).highPrice()).isEqualByComparingTo("170");
    }

}
