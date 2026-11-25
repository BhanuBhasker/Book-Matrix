package com.bookmatrix.dto;

import java.util.Locale;

/** Admin-only: show active events, archived events, or both. The public site always uses EXCLUDE. */
public enum ArchiveFilter {
    EXCLUDE, ONLY, INCLUDE;

    public static ArchiveFilter parse(String value) {
        if (value == null || value.isBlank()) {
            return EXCLUDE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return EXCLUDE;
        }
    }
}
