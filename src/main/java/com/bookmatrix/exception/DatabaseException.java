package com.bookmatrix.exception;

import org.springframework.http.HttpStatus;

/**
 * Wraps a checked java.sql.SQLException into an unchecked exception.
 * The real SQL error is logged on the server; the client only sees a safe message.
 */
public class DatabaseException extends BookMatrixException {

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override public HttpStatus status() { return HttpStatus.INTERNAL_SERVER_ERROR; }
    @Override public String code() { return "DATABASE_ERROR"; }
}
