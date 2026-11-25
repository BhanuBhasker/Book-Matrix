package com.bookmatrix.exception;

import org.springframework.http.HttpStatus;

/** Logged in, but not allowed to do this (e.g. a normal user calling an admin API). */
public class UnauthorizedActionException extends BookMatrixException {

    public UnauthorizedActionException(String message) {
        super(message);
    }

    @Override public HttpStatus status() { return HttpStatus.FORBIDDEN; }
    @Override public String code() { return "FORBIDDEN"; }
}
