package com.bookmatrix.exception;

import org.springframework.http.HttpStatus;

/** Something that was asked for does not exist (or the caller is not allowed to know it exists). */
public class NotFoundException extends BookMatrixException {

    public NotFoundException(String message) {
        super(message);
    }

    @Override public HttpStatus status() { return HttpStatus.NOT_FOUND; }
    @Override public String code() { return "NOT_FOUND"; }
}
