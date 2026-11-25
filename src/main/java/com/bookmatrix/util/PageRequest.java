package com.bookmatrix.util;

/**
 * Safe pagination input. Query parameters arrive as Integer (wrapper class, may be null);
 * here they are unboxed into int and clamped so nobody can ask for page -5 or 1,000,000 rows.
 */
public record PageRequest(int page, int size) {

    public static final int DEFAULT_SIZE = 12;
    public static final int MAX_SIZE = 100;

    public static PageRequest of(Integer page, Integer size) {
        int safePage = (page == null || page < 1) ? 1 : page;          // auto-unboxing: Integer -> int
        int safeSize = (size == null || size < 1) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return new PageRequest(safePage, safeSize);
    }

    public int offset() {
        return (page - 1) * size;
    }
}
