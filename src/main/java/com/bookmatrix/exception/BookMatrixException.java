package com.bookmatrix.exception;

import org.springframework.http.HttpStatus;

/**
 * Root of all BookMatrix business errors.
 *
 * It is an UNCHECKED exception (extends RuntimeException), so service methods do not
 * have to declare it. Every subclass overrides status() and code() — the global
 * exception handler uses polymorphism to turn any of them into the right HTTP response.
 */
public abstract class BookMatrixException extends RuntimeException {

    protected BookMatrixException(String message) {
        super(message);
    }

    protected BookMatrixException(String message, Throwable cause) {
        super(message, cause);
    }

    /** HTTP status sent to the client. */
    public abstract HttpStatus status();

    /** Stable machine-readable error code, e.g. "EVENT_NOT_FOUND". */
    public abstract String code();
}
