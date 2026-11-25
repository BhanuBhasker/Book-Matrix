package com.bookmatrix.exception;

import org.springframework.http.HttpStatus;

/** MySQL cannot be reached. Returned as 503 so the UI can say "try again shortly". */
public class DatabaseUnavailableException extends DatabaseException {

    public DatabaseUnavailableException(Throwable cause) {
        super("The database is currently unavailable. Please try again in a moment.", cause);
    }

    @Override public HttpStatus status() { return HttpStatus.SERVICE_UNAVAILABLE; }
    @Override public String code() { return "DATABASE_UNAVAILABLE"; }
}
