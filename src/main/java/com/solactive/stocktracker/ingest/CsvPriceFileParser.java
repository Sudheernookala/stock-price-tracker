package com.solactive.stocktracker.ingest;

import com.solactive.stocktracker.exception.InvalidFileException;
import java.io.BufferedReader;
import java.io.FilterReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Streaming CSV implementation of {@link PriceFileParser} based on Apache Commons CSV.
 *
 * <p>Columns are located by header name (case-insensitive), so column order does not matter and
 * extra columns are ignored. {@code volume} is the only optional column. Blank lines are skipped
 * but still counted, so reported line numbers match the physical file.
 * Rows with fewer values than the header are still validated: the missing values are treated as
 * absent, which makes a short row fail only when a required field is missing.</p>
 */
@Component
@RequiredArgsConstructor
public class CsvPriceFileParser implements PriceFileParser {

    private static final String COL_SYMBOL = "stockSymbol";
    private static final String COL_PRICE = "price";
    private static final String COL_TIMESTAMP = "timestamp";
    private static final String COL_EXCHANGE = "exchange";
    private static final String COL_VOLUME = "volume";

    private static final List<String> REQUIRED_COLUMNS = List.of(COL_SYMBOL, COL_PRICE, COL_TIMESTAMP, COL_EXCHANGE);

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreHeaderCase(true)
            .setIgnoreEmptyLines(false)
            .setIgnoreSurroundingSpaces(true)
            .setTrim(true)
            .build();

    private final PriceRowValidator validator;

    /** {@inheritDoc} */
    @Override
    public void parse(String fileName, InputStream input, Consumer<ParsedRow> sink) {
        var reader = new FailureTrackingReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        try (CSVParser parser = CSVParser.parse(new BufferedReader(reader), FORMAT)) {
            verifyHeader(fileName, parser);
            for (CSVRecord csvRecord : parser) {
                if (!isBlankLine(csvRecord)) {
                    sink.accept(validator.validate(toRawRow(csvRecord)));
                }
            }
        } catch (IOException | UncheckedIOException ex) {
            // Commons CSV reports both a failing stream and broken CSV (e.g. an unterminated quote)
            // as I/O errors; the tracking reader tells which one it was.
            throw reader.failed()
                    ? InvalidFileException.unreadable(fileName, rootMessage(ex))
                    : InvalidFileException.malformed(fileName, rootMessage(ex));
        } catch (IllegalStateException | IllegalArgumentException ex) {
            // Raised for structural problems, e.g. duplicate header names.
            throw InvalidFileException.malformed(fileName, ex.getMessage());
        }
    }

    private static void verifyHeader(String fileName, CSVParser parser) {
        Map<String, Integer> header = parser.getHeaderMap();
        if (header == null || header.isEmpty()) {
            throw InvalidFileException.empty(fileName);
        }
        Map<String, Integer> caseInsensitive = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        caseInsensitive.putAll(header);
        List<String> missing = REQUIRED_COLUMNS.stream()
                .filter(column -> !caseInsensitive.containsKey(column))
                .toList();
        if (!missing.isEmpty()) {
            throw InvalidFileException.missingHeader(fileName, String.join(", ", missing));
        }
    }

    private static RawPriceRow toRawRow(CSVRecord csvRecord) {
        // Header is line 1 and Commons CSV numbers data records from 1. Empty lines are read as
        // records (and skipped by the caller) so that this stays equal to the physical line number.
        long lineNumber = csvRecord.getRecordNumber() + 1;
        return new RawPriceRow(
                lineNumber,
                valueOf(csvRecord, COL_SYMBOL),
                valueOf(csvRecord, COL_PRICE),
                valueOf(csvRecord, COL_TIMESTAMP),
                valueOf(csvRecord, COL_EXCHANGE),
                valueOf(csvRecord, COL_VOLUME));
    }

    private static boolean isBlankLine(CSVRecord csvRecord) {
        return csvRecord.size() == 1 && csvRecord.get(0).isEmpty();
    }

    private static String valueOf(CSVRecord csvRecord, String column) {
        return csvRecord.isMapped(column) && csvRecord.isSet(column) ? csvRecord.get(column) : null;
    }

    private static String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }

    /**
     * Reader that remembers whether the underlying stream failed, so a real read error can be told
     * apart from invalid CSV content.
     */
    private static final class FailureTrackingReader extends FilterReader {

        private boolean failed;

        private FailureTrackingReader(Reader in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            try {
                return super.read();
            } catch (IOException ex) {
                failed = true;
                throw ex;
            }
        }

        @Override
        public int read(char[] buffer, int offset, int length) throws IOException {
            try {
                return super.read(buffer, offset, length);
            } catch (IOException ex) {
                failed = true;
                throw ex;
            }
        }

        private boolean failed() {
            return failed;
        }
    }
}
