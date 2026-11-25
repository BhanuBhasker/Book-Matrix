package com.bookmatrix.validation;

import com.bookmatrix.exception.ValidationException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Collects ALL problems in a form before failing, so the user sees every error at once.
 * Methods return "this" so checks can be chained (fluent style).
 * This runs on the server — client-side checks in the browser are only for convenience.
 */
public class Validator {

    // simple, practical email rule: something@something.tld, no spaces
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9 ()-]{7,20}$");

    private final Map<String, String> errors = new LinkedHashMap<>();

    public Validator required(String field, Object value, String message) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            addError(field, message);
        }
        return this;
    }

    public Validator length(String field, String value, int min, int max) {
        if (value != null && (value.length() < min || value.length() > max)) {
            addError(field, "Must be between " + min + " and " + max + " characters.");
        }
        return this;
    }

    public Validator email(String field, String value) {
        if (value != null && (value.length() > 254 || !EMAIL.matcher(value).matches())) {
            addError(field, "Enter a valid email address.");
        }
        return this;
    }

    public Validator phone(String field, String value) {
        if (value != null && !PHONE.matcher(value).matches()) {
            addError(field, "Enter a valid phone number (digits, spaces, +, -, brackets).");
        }
        return this;
    }

    public Validator range(String field, Integer value, int min, int max, String message) {
        if (value != null && (value < min || value > max)) {
            addError(field, message);
        }
        return this;
    }

    public Validator money(String field, BigDecimal value, BigDecimal max) {
        if (value != null) {
            if (value.signum() < 0) {
                addError(field, "Price cannot be negative.");
            } else if (value.compareTo(max) > 0) {
                addError(field, "Price is too high.");
            } else if (value.stripTrailingZeros().scale() > 2) {
                addError(field, "Use at most 2 decimal places.");
            }
        }
        return this;
    }

    /** Generic rule: add the message when the condition is false. */
    public Validator check(boolean condition, String field, String message) {
        if (!condition) {
            addError(field, message);
        }
        return this;
    }

    public boolean hasError(String field) {
        return errors.containsKey(field);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public void throwIfInvalid() {
        if (!errors.isEmpty()) {
            String first = errors.values().iterator().next();
            String summary = errors.size() == 1 ? first : "Please fix the " + errors.size() + " highlighted fields.";
            throw new ValidationException(summary, errors);
        }
    }

    private void addError(String field, String message) {
        errors.putIfAbsent(field, message);   // keep the first error per field
    }
}
