package com.solactive.stocktracker.service.impl;

import com.solactive.stocktracker.common.Symbols;
import com.solactive.stocktracker.dto.DailySummaryResponse;
import com.solactive.stocktracker.dto.StockSummaryResponse;
import com.solactive.stocktracker.exception.ResourceNotFoundException;
import com.solactive.stocktracker.repository.SummaryRepository;
import com.solactive.stocktracker.service.StockSummaryService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Default {@link StockSummaryService}.
 *
 * <p>When no date is given, the "latest day" depends on the question: for one symbol it is that
 * symbol's latest day (so a symbol that did not trade on the newest day is still found); for the
 * list of all symbols it is the latest day of any symbol.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockSummaryServiceImpl implements StockSummaryService {

    private final SummaryRepository repository;

    /** {@inheritDoc} */
    @Override
    public List<StockSummaryResponse> getDailySummaries(LocalDate tradingDate) {
        LocalDate date = tradingDate != null ? tradingDate : repository.findLatestTradingDate().orElse(null);
        if (date == null) {
            log.debug("No summaries yet: nothing has been processed");
            return List.of();
        }
        if (tradingDate == null) {
            log.debug("No date given; using the latest trading day with data: {}", date);
        }
        return repository.findByTradingDate(date).stream().map(StockSummaryResponse::from).toList();
    }

    /** {@inheritDoc} */
    @Override
    public StockSummaryResponse getSymbolSummary(String symbol, LocalDate tradingDate) {
        String normalised = Symbols.normalize(symbol);
        LocalDate date = tradingDate != null ? tradingDate
                : repository.findLatestTradingDate(normalised)
                        .orElseThrow(() -> ResourceNotFoundException.symbol(normalised));
        if (tradingDate == null) {
            log.debug("No date given; using the latest trading day of {}: {}", normalised, date);
        }
        return repository.findBySymbolAndTradingDate(normalised, date)
                .map(StockSummaryResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.summary(normalised, date));
    }

    /** {@inheritDoc} */
    @Override
    public List<DailySummaryResponse> getSymbolHistory(String symbol) {
        String normalised = Symbols.normalize(symbol);
        List<DailySummaryResponse> history = repository.findBySymbol(normalised).stream()
                .map(DailySummaryResponse::from)
                .toList();
        if (history.isEmpty()) {
            throw ResourceNotFoundException.symbol(normalised);
        }
        return history;
    }
}
