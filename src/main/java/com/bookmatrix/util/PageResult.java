package com.bookmatrix.util;

import java.util.List;
import java.util.function.Function;

/** Generic page of results: works for events, bookings, users... */
public record PageResult<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> PageResult<T> of(List<T> items, PageRequest request, long totalItems) {
        int totalPages = (int) Math.max(1, (totalItems + request.size() - 1) / request.size());
        return new PageResult<>(items, request.page(), request.size(), totalItems, totalPages);
    }

    /**
     * Converts every item with the given function.
     * "? super T" = the function may accept T or any parent of T (a wildcard).
     */
    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        List<R> mapped = items.stream().<R>map(mapper).toList();
        return new PageResult<>(mapped, page, size, totalItems, totalPages);
    }
}
