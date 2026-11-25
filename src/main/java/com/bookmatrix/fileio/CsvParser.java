package com.bookmatrix.fileio;

import com.bookmatrix.exception.CsvFormatException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * A small, strict CSV reader (RFC 4180). It reads one character at a time and handles:
 *  - commas inside quoted values           "Mumbai, India"
 *  - escaped quotes                        "The ""Best"" Show"
 *  - line breaks inside quoted values      "line one
 *                                           line two"
 *  - Windows (\r\n) and Unix (\n) line endings
 * Broken input (e.g. a quote that is never closed) raises the CHECKED CsvFormatException.
 */
public final class CsvParser {

    /** One parsed record and the line number it started on (for helpful error messages). */
    public record CsvRecord(int lineNumber, List<String> fields) {
        public boolean isBlank() {
            return fields.stream().allMatch(String::isBlank);
        }
    }

    private CsvParser() {
    }

    public static List<CsvRecord> parse(Reader source) throws IOException, CsvFormatException {
        // mark()/reset() is needed to peek one character ahead; BufferedReader supports it
        Reader reader = source.markSupported() ? source : new BufferedReader(source);
        List<CsvRecord> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean afterClosingQuote = false;
        boolean fieldWasQuoted = false;
        int line = 1;
        int recordStartLine = 1;
        int ch;

        while ((ch = reader.read()) != -1) {
            char c = (char) ch;
            if (inQuotes) {
                if (c == '"') {
                    reader.mark(1);
                    int next = reader.read();
                    if (next == '"') {
                        field.append('"');              // "" inside quotes = one literal quote
                    } else {
                        inQuotes = false;
                        afterClosingQuote = true;
                        if (next != -1) {
                            reader.reset();
                        }
                    }
                } else {
                    if (c == '\n') {
                        line++;
                    }
                    field.append(c);
                }
                continue;
            }
            switch (c) {
                case ',' -> {
                    fields.add(field.toString());
                    field.setLength(0);
                    afterClosingQuote = false;
                    fieldWasQuoted = false;
                }
                case '\r' -> { /* ignored: handled by the following \n */ }
                case '\n' -> {
                    fields.add(field.toString());
                    records.add(new CsvRecord(recordStartLine, List.copyOf(fields)));
                    fields.clear();
                    field.setLength(0);
                    afterClosingQuote = false;
                    fieldWasQuoted = false;
                    line++;
                    recordStartLine = line;
                }
                case '"' -> {
                    if (field.length() > 0 || fieldWasQuoted) {
                        throw new CsvFormatException(line, "unexpected quote inside an unquoted value");
                    }
                    inQuotes = true;
                    fieldWasQuoted = true;
                }
                default -> {
                    if (afterClosingQuote) {
                        throw new CsvFormatException(line, "text found after a closing quote");
                    }
                    field.append(c);
                }
            }
        }
        if (inQuotes) {
            throw new CsvFormatException(recordStartLine, "a quoted value is never closed");
        }
        if (field.length() > 0 || !fields.isEmpty() || fieldWasQuoted) {
            fields.add(field.toString());
            records.add(new CsvRecord(recordStartLine, List.copyOf(fields)));
        }
        return records;
    }
}
