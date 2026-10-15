package com.solactive.stocktracker.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.solactive.stocktracker.enums.RejectReason;
import com.solactive.stocktracker.exception.InvalidFileException;
import com.solactive.stocktracker.support.TestFixtures;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests of {@link CsvPriceFileParser}.
 */
class CsvPriceFileParserTest {

    private static InputStream csv(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Parser behaviour in isolation, with the validator mocked.
     */
    @Nested
    @ExtendWith(MockitoExtension.class)
    class WithMockedValidator {

        @Mock
        private PriceRowValidator validator;

        @Test
        @DisplayName("columns are mapped by header name, in any order and case; extra columns ignored")
        void mapsColumnsByName() {
            when(validator.validate(any())).thenReturn(new ParsedRow.Rejected(2, RejectReason.MISSING_FIELD, "x"));
            var parser = new CsvPriceFileParser(validator);

            parser.parse("f.csv", csv("""
                    Volume,EXCHANGE,timestamp,extra,price,stocksymbol
                    10,NYSE,2026-09-16T14:39:32Z,ignored,1.5,AAPL
                    """), row -> { });

            ArgumentCaptor<RawPriceRow> captor = ArgumentCaptor.forClass(RawPriceRow.class);
            verify(validator).validate(captor.capture());
            assertThat(captor.getValue()).isEqualTo(
                    new RawPriceRow(2, "AAPL", "1.5", "2026-09-16T14:39:32Z", "NYSE", "10"));
        }

        @Test
        @DisplayName("a header without required columns is rejected and names the missing columns")
        void missingHeaderColumns() {
            var parser = new CsvPriceFileParser(validator);

            assertThatThrownBy(() -> parser.parse("bad.csv", csv("symbol,price\nAAPL,1\n"), row -> { }))
                    .isInstanceOf(InvalidFileException.class)
                    .hasMessage("The file 'bad.csv' is missing required header column(s): stockSymbol, timestamp, exchange");
        }

        @Test
        @DisplayName("structurally broken CSV (unterminated quote) is rejected as malformed")
        void malformedCsv() {
            var parser = new CsvPriceFileParser(validator);

            assertThatThrownBy(() -> parser.parse("q.csv",
                    csv("stockSymbol,price,timestamp,exchange\n\"AAPL,1,t,X\n"), row -> { }))
                    .isInstanceOf(InvalidFileException.class)
                    .hasMessageStartingWith("The file 'q.csv' is not valid CSV: ");
        }

    }

    /**
     * Parser and real validator against the provided sample file.
     */
    @Nested
    class WithSampleFile {

        @Test
        @DisplayName("sample file: 107 data rows, 102 accepted, 5 rejected at the expected lines")
        void parsesSampleFile() throws IOException {
            var parser = new CsvPriceFileParser(new PriceRowValidator(TestFixtures.properties()));
            List<ParsedRow> rows = new ArrayList<>();

            try (InputStream in = Files.newInputStream(Path.of("sample-data/stock-prices.csv"))) {
                parser.parse("stock-prices.csv", in, rows::add);
            }

            assertThat(rows).hasSize(107);
            assertThat(rows).filteredOn(ParsedRow.Accepted.class::isInstance).hasSize(102);
            assertThat(rows).filteredOn(ParsedRow.Rejected.class::isInstance)
                    .extracting(ParsedRow::lineNumber)
                    .containsExactly(103L, 104L, 105L, 106L, 107L);
        }
    }
}
