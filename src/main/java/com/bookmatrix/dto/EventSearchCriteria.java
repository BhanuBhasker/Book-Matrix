package com.bookmatrix.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Everything the event search can filter on. Fields are nullable wrapper types
 * (Integer, BigDecimal...) because "not provided" must be different from "0".
 */
public record EventSearchCriteria(
        String text,
        Integer categoryId,
        String city,
        LocalDate dateFrom,
        LocalDate dateTo,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        EventLifecycleFilter lifecycle,
        ArchiveFilter archive,
        boolean featuredOnly,
        EventSort sort) {

    public static EventSearchCriteria upcoming() {
        return new EventSearchCriteria(null, null, null, null, null, null, null,
                EventLifecycleFilter.UPCOMING, ArchiveFilter.EXCLUDE, false, EventSort.DATE_ASC);
    }

    public EventSearchCriteria withFeaturedOnly() {
        return new EventSearchCriteria(text, categoryId, city, dateFrom, dateTo, minPrice, maxPrice,
                lifecycle, archive, true, sort);
    }
}
