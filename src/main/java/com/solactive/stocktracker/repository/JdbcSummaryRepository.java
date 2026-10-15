package com.solactive.stocktracker.repository;

import com.solactive.stocktracker.ingest.HighLow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL implementation of {@link SummaryRepository}, stored in table {@code daily_summary}.
 *
 * <p>Merging uses {@code INSERT ... ON CONFLICT DO UPDATE}. PostgreSQL runs it atomically per row
 * (the existing row is locked), so two files processed at the same time cannot lose each other's
 * high or low. Inside {@code DO UPDATE}, {@code s.*} is the stored row and {@code EXCLUDED.*} the
 * new values. The conditions apply the same rule as {@link HighLow#merge(HighLow)}: higher high,
 * lower low, earliest timestamp on a tie.</p>
 */
@Repository
@RequiredArgsConstructor
public class JdbcSummaryRepository implements SummaryRepository {

    /*
     * Row comparisons express the tie rule in one line each:
     *   (new price, old time) > (old price, new time)  = higher price, or same price but earlier time
     *   (new price, new time) < (old price, old time)  = lower price, or same price but earlier time
     */
    private static final String UPSERT_SQL = """
            INSERT INTO daily_summary AS s
                (stock_symbol, trading_date, high_price, high_timestamp, low_price, low_timestamp, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (stock_symbol, trading_date) DO UPDATE SET
                high_price     = CASE WHEN (EXCLUDED.high_price, s.high_timestamp) > (s.high_price, EXCLUDED.high_timestamp)
                                      THEN EXCLUDED.high_price ELSE s.high_price END,
                high_timestamp = CASE WHEN (EXCLUDED.high_price, s.high_timestamp) > (s.high_price, EXCLUDED.high_timestamp)
                                      THEN EXCLUDED.high_timestamp ELSE s.high_timestamp END,
                low_price      = CASE WHEN (EXCLUDED.low_price, EXCLUDED.low_timestamp) < (s.low_price, s.low_timestamp)
                                      THEN EXCLUDED.low_price ELSE s.low_price END,
                low_timestamp  = CASE WHEN (EXCLUDED.low_price, EXCLUDED.low_timestamp) < (s.low_price, s.low_timestamp)
                                      THEN EXCLUDED.low_timestamp ELSE s.low_timestamp END,
                updated_at     = now()
            """;

    private static final String SELECT_COLUMNS =
            "SELECT stock_symbol, trading_date, high_price, high_timestamp, low_price, low_timestamp FROM daily_summary ";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void mergeAll(Collection<HighLow> highLows) {
        if (highLows.isEmpty()) {
            return;
        }
        // Sorted so that concurrent merges lock rows in the same order and cannot deadlock.
        List<Object[]> args = highLows.stream()
                .sorted(Comparator.comparing(HighLow::stockSymbol).thenComparing(HighLow::tradingDate))
                .map(h -> new Object[] {
                        h.stockSymbol(),
                        h.tradingDate(),
                        h.highPrice(),
                        OffsetDateTime.ofInstant(h.highTimestamp(), ZoneOffset.UTC),
                        h.lowPrice(),
                        OffsetDateTime.ofInstant(h.lowTimestamp(), ZoneOffset.UTC)})
                .toList();
        jdbcTemplate.batchUpdate(UPSERT_SQL, args);
    }

    @Override
    public List<HighLow> findByTradingDate(LocalDate tradingDate) {
        return jdbcTemplate.query(SELECT_COLUMNS + "WHERE trading_date = ? ORDER BY stock_symbol",
                JdbcSummaryRepository::mapRow, tradingDate);
    }

    @Override
    public Optional<HighLow> findBySymbolAndTradingDate(String stockSymbol, LocalDate tradingDate) {
        return jdbcTemplate.query(SELECT_COLUMNS + "WHERE stock_symbol = ? AND trading_date = ?",
                JdbcSummaryRepository::mapRow, stockSymbol, tradingDate).stream().findFirst();
    }

    @Override
    public Optional<LocalDate> findLatestTradingDate() {
        return Optional.ofNullable(
                jdbcTemplate.queryForObject("SELECT max(trading_date) FROM daily_summary", LocalDate.class));
    }

    @Override
    public Optional<LocalDate> findLatestTradingDate(String stockSymbol) {
        return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT max(trading_date) FROM daily_summary WHERE stock_symbol = ?", LocalDate.class, stockSymbol));
    }

    @Override
    public List<HighLow> findBySymbol(String stockSymbol) {
        return jdbcTemplate.query(SELECT_COLUMNS + "WHERE stock_symbol = ? ORDER BY trading_date",
                JdbcSummaryRepository::mapRow, stockSymbol);
    }

    private static HighLow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new HighLow(
                rs.getString("stock_symbol"),
                rs.getObject("trading_date", LocalDate.class),
                rs.getBigDecimal("high_price"),
                rs.getObject("high_timestamp", OffsetDateTime.class).toInstant(),
                rs.getBigDecimal("low_price"),
                rs.getObject("low_timestamp", OffsetDateTime.class).toInstant());
    }
}
