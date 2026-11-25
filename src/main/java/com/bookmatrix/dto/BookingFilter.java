package com.bookmatrix.dto;

import com.bookmatrix.db.SqlWhere;

import java.time.LocalDateTime;
import java.util.Locale;

/** Filter for booking lists ("My bookings" and the admin booking table). */
public enum BookingFilter {
    ALL, CONFIRMED, PENDING, CANCELLED, EXPIRED, UPCOMING, PAST;

    public void apply(SqlWhere where, LocalDateTime now) {
        switch (this) {
            case CONFIRMED -> where.add("b.status = 'CONFIRMED'");
            case PENDING -> where.add("b.status = 'PENDING_PAYMENT'");
            case CANCELLED -> where.add("b.status IN ('CANCELLED', 'EXPIRED')");
            case EXPIRED -> where.add("b.status = 'EXPIRED'");
            case UPCOMING -> where.add("b.status IN ('CONFIRMED', 'PENDING_PAYMENT') AND e.starts_at > ?", now);
            case PAST -> where.add("e.ends_at <= ?", now);
            case ALL -> { }
        }
    }

    public static BookingFilter parse(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ALL;
        }
    }
}
