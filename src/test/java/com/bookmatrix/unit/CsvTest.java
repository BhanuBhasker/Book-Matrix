package com.bookmatrix.unit;

import com.bookmatrix.exception.CsvFormatException;
import com.bookmatrix.fileio.CsvParser;
import com.bookmatrix.fileio.CsvParser.CsvRecord;
import com.bookmatrix.fileio.CsvWriter;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvTest {

    private static List<CsvRecord> parse(String text) throws Exception {
        return CsvParser.parse(new StringReader(text));
    }

    @Test
    void parsesCommasQuotesNewlinesAndEmptyValues() throws Exception {
        List<CsvRecord> rows = parse("title,note,qty\r\n\"Mumbai, India\",\"He said \"\"hi\"\"\",2\n\"line one\nline two\",,\n");
        assertThat(rows).hasSize(3);
        assertThat(rows.get(1).fields()).containsExactly("Mumbai, India", "He said \"hi\"", "2");
        assertThat(rows.get(2).fields()).containsExactly("line one\nline two", "", "");
        assertThat(rows.get(2).lineNumber()).isEqualTo(3);
    }

    @Test
    void lastLineWithoutNewlineIsKept() throws Exception {
        assertThat(parse("a,b\n1,2")).hasSize(2);
    }

    @Test
    void rejectsBrokenQuoting() {
        assertThatThrownBy(() -> parse("a,b\n\"never closed,1\n")).isInstanceOf(CsvFormatException.class)
                .hasMessageContaining("never closed");
        assertThatThrownBy(() -> parse("a,b\nab\"c,1\n")).isInstanceOf(CsvFormatException.class);
        assertThatThrownBy(() -> parse("a,b\n\"x\"y,1\n")).isInstanceOf(CsvFormatException.class);
    }

    @Test
    void writerQuotesOnlyWhenNeededAndRoundTrips() throws Exception {
        StringWriter out = new StringWriter();
        List<String> values = List.of("plain", "with, comma", "with \"quote\"", "multi\nline", "", " padded ");
        CsvWriter.writeRow(out, values);
        assertThat(out.toString()).startsWith("plain,\"with, comma\",\"with \"\"quote\"\"\",\"multi\nline\",,\" padded \"");
        assertThat(parse(out.toString()).get(0).fields()).containsExactlyElementsOf(values);
    }

    @Test
    void writerNeutralisesSpreadsheetFormulas() {
        assertThat(CsvWriter.escape("=HYPERLINK(\"x\")")).startsWith("\"'=");
        assertThat(CsvWriter.escape("-12.5")).isEqualTo("-12.5");   // real negative numbers stay numbers
        assertThat(CsvWriter.escape(null)).isEmpty();
    }
}
