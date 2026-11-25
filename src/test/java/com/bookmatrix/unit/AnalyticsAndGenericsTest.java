package com.bookmatrix.unit;

import com.bookmatrix.analytics.EventAnalytics;
import com.bookmatrix.dao.StatsDao.InventoryRow;
import com.bookmatrix.demo.GenericsDemo;
import com.bookmatrix.model.Event;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Streams (EventAnalytics) and generics. */
class AnalyticsAndGenericsTest {

    private final LocalDateTime now = LocalDateTime.of(2027, 1, 1, 10, 0);

    private Event ev(String title, String cat, String city, String price, int cap, int sold, int days) {
        Event e = new Event();
        e.setTitle(title);
        e.setCategoryName(cat);
        e.setCity(city);
        e.setTicketPrice(new BigDecimal(price));
        e.setCapacity(cap);
        e.setTicketsSold(sold);
        e.setStartsAt(now.plusDays(days));
        e.setEndsAt(now.plusDays(days).plusHours(2));
        return e;
    }

    private List<Event> events() {
        return List.of(ev("B", "Music", "Pune", "800", 100, 40, 5), ev("A", "Comedy", "Delhi", "300", 50, 50, 2),
                ev("C", "Music", "pune", "1500", 10, 1, -3), ev("D", "Theatre", "Mumbai", "1200", 80, 20, 9));
    }

    @Test
    void upcomingFiltersPastEventsAndSortsByDate() {
        assertThat(EventAnalytics.upcoming(events(), now)).extracting(Event::getTitle).containsExactly("A", "B", "D");
    }

    @Test
    void groupingDistinctHighPricedAverageAndPaging() {
        Map<String, Integer> byCat = EventAnalytics.ticketsByCategory(events());
        assertThat(byCat).containsExactly(Map.entry("Comedy", 50), Map.entry("Music", 41), Map.entry("Theatre", 20));
        assertThat(EventAnalytics.distinctCities(events())).containsExactly("Delhi", "Mumbai", "Pune", "pune");
        assertThat(EventAnalytics.highPriced(events(), new BigDecimal("1000"))).extracting(Event::getTitle).containsExactly("C", "D");
        assertThat(EventAnalytics.averageTicketPrice(events()).orElseThrow()).isEqualTo(950.0);
        assertThat(EventAnalytics.averageTicketPrice(List.of())).isEmpty();
        assertThat(EventAnalytics.page(List.of(1, 2, 3, 4, 5), 2, 2)).containsExactly(3, 4);
        assertThat(EventAnalytics.inCategory("music").test(events().get(0))).isTrue();
    }

    @Test
    void topSellingRevenueAndConsistency() {
        List<InventoryRow> rows = List.of(
                new InventoryRow(1, "X", "Pune", now, "SCHEDULED", false, 100, 70, 60, 10, null, new BigDecimal("700")),
                new InventoryRow(2, "Y", "Pune", now, "SCHEDULED", false, 100, 90, 89, 0, null, new BigDecimal("900")),
                new InventoryRow(3, "Z", "Pune", now, "SCHEDULED", false, 100, 0, 0, 0, 0L, BigDecimal.ZERO),
                new InventoryRow(4, "W", "Pune", now, "SCHEDULED", false, 20, 4, 4, 0, 3L, BigDecimal.ZERO));
        assertThat(EventAnalytics.topSelling(rows, 5)).extracting(InventoryRow::title).containsExactly("Y", "X", "W");
        assertThat(EventAnalytics.totalRevenue(rows)).isEqualByComparingTo("1600");
        assertThat(EventAnalytics.countInconsistent(rows)).as("Y: 90 != 89 booked; W: 4 sold but only 3 seats").isEqualTo(2);
        assertThat(rows.get(0).consistent()).as("70 sold = 60 paid + 10 held").isTrue();
    }

    @Test
    void genericMethodsBoundsAndWildcards() {
        assertThat(GenericsDemo.firstOrDefault(List.<String>of(), "none")).isEqualTo("none");
        assertThat(GenericsDemo.sum(List.of(1, 2, 3))).isEqualTo(6.0);
        assertThat(GenericsDemo.sum(List.of(1.5, 2.5))).isEqualTo(4.0);
        assertThat(GenericsDemo.max(List.of("Comedy", "Theatre", "Music"))).isEqualTo("Theatre");
        assertThat(GenericsDemo.average(List.of(2, 4))).isEqualTo(3.0);
        List<Number> sink = new ArrayList<>();
        GenericsDemo.addTicketNumbers(sink, 3);
        assertThat(sink).containsExactly(1, 2, 3);
        assertThat(new GenericsDemo.Box<>("x").get()).isEqualTo("x");
        assertThat(GenericsDemo.countAll(List.of(1, "a"))).isEqualTo(2);
    }
}
