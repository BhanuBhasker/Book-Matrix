package com.bookmatrix.analytics;

import com.bookmatrix.dao.StatsDao.InventoryRow;
import com.bookmatrix.model.Event;
import com.bookmatrix.model.EventDisplayStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Analytics written with the Stream API and lambdas. Used by the admin dashboard.
 * A stream pipeline = source -> intermediate operations (lazy) -> one terminal operation.
 * Every method is a pure function (no database access), so it is easy to unit test.
 */
public final class EventAnalytics {

    private EventAnalytics() {
    }

    /** Reusable predicates (functional interface Predicate<T>) built with lambdas. */
    public static Predicate<Event> isUpcoming(LocalDateTime now) {
        return e -> e.isBookable(now) || e.getDisplayStatus(now) == EventDisplayStatus.SOLD_OUT;
    }

    public static Predicate<Event> inCategory(String category) {
        return e -> e.getCategoryName().equalsIgnoreCase(category);
    }

    /** filter + sorted: upcoming events, soonest first (natural order via Comparable). */
    public static List<Event> upcoming(List<Event> events, LocalDateTime now) {
        return events.stream()
                .filter(isUpcoming(now))
                .sorted()
                .toList();
    }

    /** sorted with a Comparator + limit: the n best-selling events. */
    public static List<InventoryRow> topSelling(List<InventoryRow> rows, int n) {
        return rows.stream()
                .filter(r -> r.ticketsSold() > 0)
                .sorted(Comparator.comparingInt(InventoryRow::ticketsSold).reversed()
                        .thenComparing(InventoryRow::title))
                .limit(n)
                .toList();
    }

    /** groupingBy into a TreeMap (sorted keys): tickets sold per category. */
    public static Map<String, Integer> ticketsByCategory(List<Event> events) {
        return events.stream()
                .collect(Collectors.groupingBy(Event::getCategoryName, TreeMap::new,
                        Collectors.summingInt(Event::getTicketsSold)));
    }

    /** map + distinct + sorted: the cities that have events. */
    public static List<String> distinctCities(List<Event> events) {
        return events.stream()
                .map(Event::getCity)          // method reference
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    /** filter on BigDecimal: events priced at or above a threshold, most expensive first. */
    public static List<Event> highPriced(List<Event> events, BigDecimal threshold) {
        return events.stream()
                .filter(e -> e.getTicketPrice().compareTo(threshold) >= 0)
                .sorted(Comparator.comparing(Event::getTicketPrice).reversed())
                .toList();
    }

    /** skip + limit = in-memory pagination. */
    public static <T> List<T> page(List<T> items, int pageNumber, int pageSize) {
        return items.stream()
                .skip((long) (pageNumber - 1) * pageSize)
                .limit(pageSize)
                .toList();
    }

    /** mapToDouble + average returns OptionalDouble (empty when the list is empty). */
    public static OptionalDouble averageTicketPrice(List<Event> events) {
        return events.stream().mapToDouble(e -> e.getTicketPrice().doubleValue()).average();
    }

    /** reduce: total revenue across inventory rows. */
    public static BigDecimal totalRevenue(List<InventoryRow> rows) {
        return rows.stream().map(InventoryRow::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static long countInconsistent(List<InventoryRow> rows) {
        return rows.stream().filter(r -> !r.consistent()).count();
    }
}
