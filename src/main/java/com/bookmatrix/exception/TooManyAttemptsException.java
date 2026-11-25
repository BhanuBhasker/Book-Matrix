package com.bookmatrix.exception;

import org.springframework.http.HttpStatus;

/** Too many failed logins for one email in a short time. */
public class TooManyAttemptsException extends BookMatrixException {

    public TooManyAttemptsException() {
        super("Too many failed login attempts. Please wait 5 minutes and try again.");
    }

    @Override public HttpStatus status() { return HttpStatus.TOO_MANY_REQUESTS; }
    @Override public String code() { return "TOO_MANY_ATTEMPTS"; }
}
