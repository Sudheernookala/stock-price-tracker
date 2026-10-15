package com.solactive.stocktracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solactive.stocktracker.ingest.HighLow;
import com.solactive.stocktracker.repository.SummaryRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end tests against a real PostgreSQL (Testcontainers): Flyway schema, the summary upsert,
 * status records and the folder scan. Skipped automatically when Docker is not available.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class StockPriceTrackerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Path ROOT = createTempRoot();
    private static final Path FOLDER = ROOT.resolve("input");
    private static final LocalDate DAY = LocalDate.of(2026, 9, 16);
    private static final Path SAMPLE_FILE = Path.of("sample-data/stock-prices.csv");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SummaryRepository summaryRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.folder.dir", FOLDER::toString);
    }

    @BeforeEach
    void cleanDatabaseAndFolder() throws IOException {
        jdbcTemplate.execute("TRUNCATE file_import, file_import_error_summary, daily_summary RESTART IDENTITY CASCADE");
        Files.createDirectories(FOLDER);
        try (var files = Files.list(FOLDER)) {
            for (Path file : files.toList()) {
                Files.delete(file);
            }
        }
    }

    /** Waits until the background folder scan has finished and returns its final state. */
    private ResultActions awaitScan() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (true) {
            ResultActions latest = mockMvc.perform(get("/api/v1/folder-scans/latest")).andExpect(status().isOk());
            String status = objectMapper.readTree(latest.andReturn().getResponse().getContentAsString())
                    .get("status").asText();
            if (!"RUNNING".equals(status) || System.nanoTime() > deadline) {
                return latest;
            }
            Thread.sleep(50);
        }
    }

    private long recordCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM file_import", Long.class);
    }

    @Test
    @DisplayName("folder scan: files processed in order and left in place; handled content is skipped without new records")
    void folderScanEndToEnd() throws Exception {
        copySample(FOLDER.resolve("a_prices.csv"));
        Files.writeString(FOLDER.resolve("b_broken.csv"), "symbol,cost\nAAPL,1\n");
        copySample(FOLDER.resolve("c_same_content.csv"));

        // First scan: a is processed, b fails, c has the same content as a and is skipped.
        mockMvc.perform(post("/api/v1/folder-scans"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.filesFound").value(3));
        awaitScan()
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.filesFound").value(3))
                .andExpect(jsonPath("$.processed.length()").value(1))
                .andExpect(jsonPath("$.processed[0].fileName").value("a_prices.csv"))
                .andExpect(jsonPath("$.processed[0].status").value("COMPLETED_WITH_ERRORS"))
                .andExpect(jsonPath("$.processed[0].acceptedRows").value(102))
                .andExpect(jsonPath("$.processed[0].rejectedRows").value(5))
                .andExpect(jsonPath("$.failed.length()").value(1))
                .andExpect(jsonPath("$.failed[0].fileName").value("b_broken.csv"))
                .andExpect(jsonPath("$.failed[0].status").value("FAILED"))
                .andExpect(jsonPath("$.failed[0].errorMessage").value(
                        "The file 'b_broken.csv' is missing required header column(s): stockSymbol, price, timestamp, exchange"))
                .andExpect(jsonPath("$.alreadyProcessed[0]").value("c_same_content.csv"));
        assertThat(recordCount()).isEqualTo(2);
        try (var files = Files.list(FOLDER)) {
            assertThat(files).hasSize(3);   // nothing was moved
        }

        // Second scan: everything is already handled, so nothing is processed and no record is added.
        mockMvc.perform(post("/api/v1/folder-scans")).andExpect(status().isAccepted());
        awaitScan()
                .andExpect(jsonPath("$.processed.length()").value(0))
                .andExpect(jsonPath("$.alreadyProcessed.length()").value(3));
        assertThat(recordCount()).isEqualTo(2);

        // Fixing the broken file changes its content, so the next scan processes it.
        Files.writeString(FOLDER.resolve("b_broken.csv"),
                "stockSymbol,price,timestamp,exchange\nAAPL,200.000000,2026-09-16T15:00:00Z,NYSE\n");
        mockMvc.perform(post("/api/v1/folder-scans")).andExpect(status().isAccepted());
        awaitScan()
                .andExpect(jsonPath("$.processed.length()").value(1))
                .andExpect(jsonPath("$.processed[0].fileName").value("b_broken.csv"))
                .andExpect(jsonPath("$.processed[0].status").value("COMPLETED"));
        assertThat(recordCount()).isEqualTo(3);

        // The error summary is returned with the single record.
        String body = mockMvc.perform(get("/api/v1/imports/1")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode record = objectMapper.readTree(body);
        assertThat(record.get("source").asText()).isEqualTo("FOLDER");
        // Rejected rows are grouped by reason: the two missing fields first, then by first line.
        assertThat(record.get("errorSummary")).extracting(g -> g.get("code").asText() + "x" + g.get("count").asLong()
                        + "@" + g.get("firstLine").asLong())
                .containsExactly("MISSING_FIELDx2@103", "PRICE_NOT_POSITIVEx1@104", "INVALID_TIMESTAMPx1@105",
                        "PRICE_NOT_A_NUMBERx1@106");

        // Summaries come from the database and include the fixed file's new AAPL high.
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM daily_summary", Long.class)).isEqualTo(10);
        mockMvc.perform(get("/api/v1/summaries/NVDA"))
                .andExpect(jsonPath("$.highPrice").value(999.999999))
                .andExpect(jsonPath("$.highTimestamp").value("2026-09-16T13:00:00Z"))
                .andExpect(jsonPath("$.lowPrice").value(120.417882));
        mockMvc.perform(get("/api/v1/summaries/AAPL"))
                .andExpect(jsonPath("$.highPrice").value(200.0))
                .andExpect(jsonPath("$.lowPrice").value(153.215726));
    }

    @Test
    @DisplayName("uploads: an unusable file or a non-CSV file gets 400 and leaves no record; a usable one is recorded")
    void uploadsRecordOnlyUsableFiles() throws Exception {
        mockMvc.perform(multipart("/api/v1/imports")
                        .file(new MockMultipartFile("file", "bad.csv", "text/csv", "symbol,cost\nAAPL,1\n".getBytes())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/v1/imports")
                        .file(new MockMultipartFile("file", "prices.xlsx", "application/octet-stream", new byte[] {1, 2, 3})))
                .andExpect(status().isBadRequest());
        assertThat(recordCount()).isZero();

        mockMvc.perform(multipart("/api/v1/imports").file(new MockMultipartFile("file", "good.csv", "text/csv",
                        "stockSymbol,price,timestamp,exchange\nAAPL,1.000000,2026-09-16T14:00:00Z,NYSE\n".getBytes())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("UPLOAD"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(recordCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("10 simultaneous uploads of the same file: processed once; the others get 409 or SKIPPED_DUPLICATE")
    void simultaneousUploadsOfSameContent() throws Exception {
        StringBuilder csv = new StringBuilder("stockSymbol,price,timestamp,exchange\n");
        for (int i = 0; i < 100_000; i++) {
            csv.append("AAPL,").append(100 + i % 50).append(".000000,2026-09-16T14:00:00Z,NYSE\n");
        }
        byte[] content = csv.toString().getBytes();

        List<Callable<MvcResult>> uploads = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            uploads.add(() -> mockMvc.perform(multipart("/api/v1/imports")
                    .file(new MockMultipartFile("file", "same.csv", "text/csv", content))).andReturn());
        }
        List<Integer> statuses = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(10)) {
            for (Future<MvcResult> result : executor.invokeAll(uploads)) {
                statuses.add(result.get().getResponse().getStatus());
            }
        }

        assertThat(statuses).containsOnly(201, 409).contains(201);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM file_import WHERE status = 'COMPLETED'", Long.class)).isEqualTo(1);
        assertThat(recordCount()).isEqualTo(statuses.stream().filter(code -> code == 201).count());
    }

    @Test
    @DisplayName("several trading days: each symbol uses its own latest day; history lists every day")
    void multipleTradingDays() throws Exception {
        Files.writeString(FOLDER.resolve("day16.csv"), """
                stockSymbol,price,timestamp,exchange
                AAPL,150.000000,2026-09-16T14:00:00Z,NYSE
                AAPL,155.000000,2026-09-16T15:00:00Z,NYSE
                MSFT,400.000000,2026-09-16T14:00:00Z,NYSE
                """);
        Files.writeString(FOLDER.resolve("day17.csv"), """
                stockSymbol,price,timestamp,exchange
                AAPL,170.000000,2026-09-17T14:00:00Z,NYSE
                AAPL,165.000000,2026-09-17T15:00:00Z,NYSE
                """);
        mockMvc.perform(post("/api/v1/folder-scans")).andExpect(status().isAccepted());
        awaitScan().andExpect(jsonPath("$.status").value("COMPLETED"));

        // MSFT did not trade on the 17th: without a date it must still return its own latest day.
        mockMvc.perform(get("/api/v1/summaries/MSFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.highPrice").value(400.0));
        mockMvc.perform(get("/api/v1/summaries/AAPL"))
                .andExpect(jsonPath("$.highPrice").value(170.0))
                .andExpect(jsonPath("$.lowPrice").value(165.0));
        mockMvc.perform(get("/api/v1/summaries/AAPL").param("date", "2026-09-16"))
                .andExpect(jsonPath("$.highPrice").value(155.0));

        mockMvc.perform(get("/api/v1/summaries/AAPL/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].tradingDate").value("2026-09-16"))
                .andExpect(jsonPath("$[0].highPrice").value(155.0))
                .andExpect(jsonPath("$[1].tradingDate").value("2026-09-17"))
                .andExpect(jsonPath("$[1].highPrice").value(170.0));
        mockMvc.perform(get("/api/v1/summaries/ZZZ/history")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("health endpoint reports UP, including the database check")
    void healthIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("database merge: higher high and lower low win; on a tie the earliest timestamp wins")
    void upsertFollowsMergeRule() {
        summaryRepository.mergeAll(List.of(highLow("100", "2026-09-16T15:00:00Z", "90", "2026-09-16T15:00:00Z")));
        summaryRepository.mergeAll(List.of(highLow("100", "2026-09-16T14:00:00Z", "95", "2026-09-16T14:00:00Z")));
        summaryRepository.mergeAll(List.of(highLow("99", "2026-09-16T13:00:00Z", "90", "2026-09-16T13:30:00Z")));

        HighLow stored = summaryRepository.findBySymbolAndTradingDate("TEST", DAY).orElseThrow();
        assertThat(stored.highPrice()).isEqualByComparingTo("100");
        assertThat(stored.highTimestamp()).isEqualTo(Instant.parse("2026-09-16T14:00:00Z"));   // tie: earlier wins
        assertThat(stored.lowPrice()).isEqualByComparingTo("90");
        assertThat(stored.lowTimestamp()).isEqualTo(Instant.parse("2026-09-16T13:30:00Z"));    // tie: earlier wins
        assertThat(stored.highPrice().scale()).isEqualTo(6);
        assertThat(summaryRepository.findLatestTradingDate()).contains(DAY);
    }

    private static HighLow highLow(String high, String highTime, String low, String lowTime) {
        return new HighLow("TEST", DAY, new BigDecimal(high), Instant.parse(highTime),
                new BigDecimal(low), Instant.parse(lowTime));
    }

    private void copySample(Path target) throws IOException {
        Files.copy(SAMPLE_FILE, target);
    }

    private static Path createTempRoot() {
        try {
            return Files.createTempDirectory("stock-tracker-it");
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
