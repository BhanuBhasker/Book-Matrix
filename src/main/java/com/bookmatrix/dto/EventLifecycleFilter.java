package com.bookmatrix.dto;

import com.bookmatrix.db.SqlWhere;

import java.time.LocalDateTime;
import java.util.Locale;

/** "Which events?" filter. Each constant adds its own fixed SQL condition (polymorphic enum). */
public enum EventLifecycleFilter {
    UPCOMING {
        @Override public void apply(SqlWhere where, LocalDateTime now) {
            where.add("e.status = 'SCHEDULED' AND e.starts_at > ?", now);
        }
    },
    AVAILABLE {
        @Override public void apply(SqlWhere where, LocalDateTime now) {
            where.add("e.status = 'SCHEDULED' AND e.starts_at > ? AND e.tickets_sold < e.capacity", now);
        }
    },
    SOLD_OUT {
        @Override public void apply(SqlWhere where, LocalDateTime now) {
            where.add("e.status = 'SCHEDULED' AND e.starts_at > ? AND e.tickets_sold >= e.capacity", now);
        }
    },
    LIVE {
        @Override public void apply(SqlWhere where, LocalDateTime now) {
            where.add("e.status = 'SCHEDULED' AND e.starts_at <= ? AND e.ends_at > ?", now, now);
        }
    },
    COMPLETED {
        @Override public void apply(SqlWhere where, LocalDateTime now) {
            where.add("e.status = 'SCHEDULED' AND e.ends_at <= ?", now);
        }
    },
    CANCELLED {
        @Override public void apply(SqlWhere where, LocalDateTime now) {
            where.add("e.status = 'CANCELLED'");
        }
    },
    ALL {
        @Override public void apply(SqlWhere where, LocalDateTime now) {
            // no restriction
        }
    };

    public abstract void apply(SqlWhere where, LocalDateTime now);

    public static EventLifecycleFilter parse(String value, EventLifecycleFilter fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
