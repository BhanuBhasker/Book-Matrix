package com.bookmatrix.exception;

import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bad input. Carries one message per invalid field (LinkedHashMap keeps the form order). */
public class ValidationException extends BookMatrixException {

    private final Map<String, String> fieldErrors;

    public ValidationException(String message) {
        this(message, Map.of());
    }

    public ValidationException(String message, Map<String, String> fieldErrors) {
        super(message);
        this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    public static ValidationException forField(String field, String message) {
        return new ValidationException(message, Map.of(field, message));
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }

    @Override public HttpStatus status() { return HttpStatus.BAD_REQUEST; }
    @Override public String code() { return "VALIDATION_FAILED"; }
}
