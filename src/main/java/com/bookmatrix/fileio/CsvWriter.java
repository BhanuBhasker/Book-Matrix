package com.bookmatrix.fileio;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * Writes RFC 4180 CSV. A value is wrapped in quotes when it contains a comma, quote or
 * newline, and quotes inside it are doubled:  He said "hi", then left  ->  "He said ""hi"", then left"
 */
public final class CsvWriter {

    private CsvWriter() {
    }

    public static void writeRow(Writer out, List<?> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.write(',');
            }
            out.write(escape(values.get(i)));
        }
        out.write("\r\n");
    }

    public static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        // Spreadsheet formula injection guard: a cell starting with = + - @ could run a formula in Excel
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0 && !isNumber(text)) {
            text = "'" + text;
        }
        boolean needsQuotes = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")
                || (!text.isEmpty() && (Character.isWhitespace(text.charAt(0))
                || Character.isWhitespace(text.charAt(text.length() - 1))));
        if (!needsQuotes) {
            return text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static boolean isNumber(String text) {
        try {
            Double.parseDouble(text);      // wrapper class parsing
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
