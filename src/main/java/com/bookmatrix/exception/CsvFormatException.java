package com.bookmatrix.exception;

/**
 * A CHECKED exception (extends Exception, not RuntimeException).
 * Any method that can throw it must declare "throws CsvFormatException",
 * and the caller is forced by the compiler to handle it.
 */
public class CsvFormatException extends Exception {

    private final int lineNumber;

    public CsvFormatException(int lineNumber, String message) {
        super("Line " + lineNumber + ": " + message);
        this.lineNumber = lineNumber;
    }

    public int getLineNumber() {
        return lineNumber;
    }
}
