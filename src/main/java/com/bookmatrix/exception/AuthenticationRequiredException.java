package com.bookmatrix.exception;

import org.springframework.http.HttpStatus;

/** Not logged in, wrong credentials, or the session expired. */
public class AuthenticationRequiredException extends BookMatrixException {

    private final String code;

    public AuthenticationRequiredException(String message) {
        this("AUTH_REQUIRED", message);
    }

    public AuthenticationRequiredException(String code, String message) {
        super(message);
        this.code = code;
    }

    @Override public HttpStatus status() { return HttpStatus.UNAUTHORIZED; }
    @Override public String code() { return code; }
}
