package com.bookmatrix.unit;

import com.bookmatrix.model.Booking;
import com.bookmatrix.model.BookingStatus;
import com.bookmatrix.model.Event;
import com.bookmatrix.model.EventDisplayStatus;
import com.bookmatrix.model.EventStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Event status rules, booking eligibility, booking maths and the cancellation policy. */
class DomainRulesTest {

    private final LocalDateTime now = LocalDateTime.of(2027, 1, 10, 12, 0);

    private Event event(int capacity, int sold, LocalDateTime start) {
        Event e = new Event();
        e.setTitle("Show");
        e.setCapacity(capacity);
        e.setTicketsSold(sold);
        e.setStartsAt(start);
        e.setEndsAt(start.plusHours(2));
        e.setTicketPrice(new BigDecimal("500"));
        return e;
    }

    @Test
    void upcomingEventIsBookable() {
        Event e = event(100, 10, now.plusDays(1));
        assertThat(e.getDisplayStatus(now)).isEqualTo(EventDisplayStatus.UPCOMING);
        assertThat(e.isBookable(now)).isTrue();
        assertThat(e.getAvailableTickets()).isEqualTo(90);
    }

    @Test
    void soldOutLiveCompletedCancelledArchived() {
        assertThat(event(100, 100, now.plusDays(1)).getDisplayStatus(now)).isEqualTo(EventDisplayStatus.SOLD_OUT);
        assertThat(event(100, 5, now.minusHours(1)).getDisplayStatus(now)).isEqualTo(EventDisplayStatus.LIVE);
        assertThat(event(100, 5, now.minusDays(1)).getDisplayStatus(now)).isEqualTo(EventDisplayStatus.COMPLETED);
        Event cancelled = event(100, 0, now.plusDays(1));
        cancelled.setStatus(EventStatus.CANCELLED);
        assertThat(cancelled.getDisplayStatus(now)).isEqualTo(EventDisplayStatus.CANCELLED);
        assertThat(cancelled.getBookingBlockReason(now)).contains("This event has been cancelled.");
        Event archived = event(100, 0, now.plusDays(1));
        archived.setArchivedAt(now);
        assertThat(archived.isBookable(now)).isFalse();
    }

    @Test
    void cancelledWinsOverSoldOutAndCompleted() {
        Event e = event(10, 10, now.minusDays(3));
        e.setStatus(EventStatus.CANCELLED);
        assertThat(e.getDisplayStatus(now)).isEqualTo(EventDisplayStatus.CANCELLED);
    }

    @Test
    void startedEventCannotBeBooked() {
        Event e = event(100, 0, now);   // starts exactly now
        assertThat(e.getBookingBlockReason(now)).contains("This event has already started.");
    }

    @Test
    void naturalOrderIsByStartTime() {
        List<Event> events = new ArrayList<>(List.of(event(1, 0, now.plusDays(3)), event(1, 0, now.plusDays(1)), event(1, 0, now.plusDays(2))));
        Collections.sort(events);
        assertThat(events).extracting(Event::getStartsAt).isSorted();
    }

    @Test
    void bookingTotalUsesBigDecimal() {
        assertThat(Booking.calculateTotal(new BigDecimal("799.50"), 3)).isEqualByComparingTo("2398.50");
        assertThat(Booking.calculateTotal(new BigDecimal("0.10"), 3)).isEqualByComparingTo("0.30");
    }

    @Test
    void cancellationPolicy() {
        Booking b = new Booking();
        b.setStatus(BookingStatus.CONFIRMED);
        b.setEventStartsAt(now.plusHours(5));
        assertThat(b.getCancellationBlockReason(now, 2)).isEmpty();
        b.setEventStartsAt(now.plusHours(1));
        assertThat(b.getCancellationBlockReason(now, 2)).isPresent();
        b.setEventStartsAt(now.plusDays(2));
        b.setStatus(BookingStatus.CANCELLED);
        assertThat(b.getCancellationBlockReason(now, 2)).contains("This booking is already cancelled.");
    }
}
