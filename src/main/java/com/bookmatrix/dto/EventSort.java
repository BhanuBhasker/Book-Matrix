package com.bookmatrix.dto;

import java.util.Locale;

/**
 * Allowed sort orders. The user only ever picks a NAME; the ORDER BY text is fixed here,
 * so sorting can never be used for SQL injection.
 */
public enum EventSort {
    DATE_ASC("e.starts_at ASC, e.id ASC"),
    DATE_DESC("e.starts_at DESC, e.id DESC"),
    PRICE_ASC("e.ticket_price ASC, e.starts_at ASC"),
    PRICE_DESC("e.ticket_price DESC, e.starts_at ASC"),
    POPULAR("e.tickets_sold DESC, e.starts_at ASC"),
    NEWEST("e.created_at DESC, e.id DESC");

    private final String orderBy;

    EventSort(String orderBy) {
        this.orderBy = orderBy;
    }

    public String orderBy() {
        return orderBy;
    }

    /** Unknown or missing values fall back to the default instead of failing. */
    public static EventSort parse(String value) {
        if (value == null || value.isBlank()) {
            return DATE_ASC;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DATE_ASC;
        }
    }
}
