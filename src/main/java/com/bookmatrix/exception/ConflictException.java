package com.bookmatrix.exception;

import org.springframework.http.HttpStatus;

/** The request clashes with existing data, e.g. duplicate email or capacity below tickets sold. */
public class ConflictException extends BookMatrixException {

    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    @Override public HttpStatus status() { return HttpStatus.CONFLICT; }
    @Override public String code() { return code; }
}
