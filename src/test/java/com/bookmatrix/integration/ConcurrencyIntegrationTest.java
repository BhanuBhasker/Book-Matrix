package com.bookmatrix.integration;

import com.bookmatrix.dto.Requests;
import com.bookmatrix.exception.BookMatrixException;
import com.bookmatrix.exception.ConflictException;
import com.bookmatrix.exception.InvalidBookingException;
import com.bookmatrix.model.Role;
import com.bookmatrix.security.SessionUser;
import com.bookmatrix.service.BookingService;
import com.bookmatrix.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Many real threads hit the real booking service (and MySQL) at the same instant.
 * The database-level protection must keep inventory exact.
 */
class ConcurrencyIntegrationTest extends IntegrationTestBase {

    @Autowired
    BookingService bookingService;

    private List<SessionUser> users(int n) {
        List<SessionUser> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(sessionUser(createUser(uniqueEmail("racer"), Role.USER)));
        }
        return list;
    }

    /** Runs all tasks at once (released together by a latch) and counts how many succeeded. */
    private <T> int runTogether(List<Callable<T>> tasks, AtomicInteger expectedFailures) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (Callable<T> task : tasks) {
            futures.add(pool.submit(() -> {
                gate.await();
                try {
                    task.call();
                    return true;
                } catch (InvalidBookingException | ConflictException e) {   // sold out / already cancelled / already paid
                    expectedFailures.incrementAndGet();
                    return false;
                }
            }));
        }
        gate.countDown();
        int ok = 0;
        for (Future<Boolean> f : futures) {
            if (f.get(60, TimeUnit.SECONDS)) {
                ok++;
            }
        }
        pool.shutdown();
        return ok;
    }

    @Test
    void thirtyUsersRaceForTenTickets() throws Exception {
        long eventId = createUpcomingEvent("Hot Ticket", 10, "500");
        List<Callable<Object>> tasks = new ArrayList<>();
        for (SessionUser u : users(30)) {
            tasks.add(() -> bookingService.book(u, new Requests.CreateBooking(eventId, 1, null)));
        }
        AtomicInteger rejected = new AtomicInteger();
        int ok = runTogether(tasks, rejected);
        assertThat(ok).isEqualTo(10);
        assertThat(rejected.get()).isEqualTo(20);
        assertThat(ticketsSold(eventId)).isEqualTo(10);
        assertThat(countRows("SELECT COUNT(*) FROM bookings WHERE event_id = ? AND status IN ('CONFIRMED','PENDING_PAYMENT')", eventId)).isEqualTo(10);
    }

    @Test
    void mixedQuantitiesNeverOversell() throws Exception {
        long eventId = createUpcomingEvent("Mixed", 25, "100");
        List<Callable<Object>> tasks = new ArrayList<>();
        int q = 1;
        for (SessionUser u : users(24)) {
            int qty = (q++ % 4) + 1;
            tasks.add(() -> bookingService.book(u, new Requests.CreateBooking(eventId, qty, null)));
        }
        runTogether(tasks, new AtomicInteger());
        int sold = ticketsSold(eventId);
        long fromBookings = countRows("SELECT COALESCE(SUM(quantity),0) FROM bookings WHERE event_id=? AND status IN ('CONFIRMED','PENDING_PAYMENT')", eventId);
        assertThat(sold).isBetween(0, 25);
        assertThat(fromBookings).isEqualTo(sold);
    }

    @Test
    void theLastTicketGoesToExactlyOneOfTwoUsers() throws Exception {
        for (int round = 0; round < 5; round++) {
            long eventId = createUpcomingEvent("Last Seat " + round, 1, "100");
            List<Callable<Object>> tasks = new ArrayList<>();
            for (SessionUser u : users(2)) {
                tasks.add(() -> bookingService.book(u, new Requests.CreateBooking(eventId, 1, null)));
            }
            assertThat(runTogether(tasks, new AtomicInteger())).isEqualTo(1);
            assertThat(ticketsSold(eventId)).isEqualTo(1);
        }
    }

    @Test
    void sameRequestKeySentTenTimesCreatesOneBooking() throws Exception {
        long eventId = createUpcomingEvent("Retry Storm", 50, "100");
        SessionUser user = users(1).get(0);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> bookingService.book(user, new Requests.CreateBooking(eventId, 3, "retry-storm-key-1")));
        }
        assertThat(runTogether(tasks, new AtomicInteger())).isEqualTo(10);     // all return the same booking
        assertThat(countRows("SELECT COUNT(*) FROM bookings WHERE event_id = ?", eventId)).isEqualTo(1);
        assertThat(ticketsSold(eventId)).isEqualTo(3);
    }

    @Test
    void simultaneousCancellationsRestoreInventoryOnce() throws Exception {
        long eventId = createUpcomingEvent("Cancel Race", 10, "100");
        SessionUser user = users(1).get(0);
        String ref = bookingService.book(user, new Requests.CreateBooking(eventId, 4, null)).booking().reference();
        assertThat(ticketsSold(eventId)).isEqualTo(4);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> bookingService.cancelMine(user, ref, null));
        }
        AtomicInteger refused = new AtomicInteger();
        assertThat(runTogether(tasks, refused)).isEqualTo(1);
        assertThat(refused.get()).isEqualTo(9);
        assertThat(ticketsSold(eventId)).isZero();
    }

    @Test
    void tenParallelPaymentsForOneBookingChargeOnce() throws Exception {
        long eventId = createUpcomingEvent("Double Pay", 10, "100");
        SessionUser user = users(1).get(0);
        String ref = bookingService.book(user, new Requests.CreateBooking(eventId, 2, null)).booking().reference();
        Requests.Pay card = new Requests.Pay("CARD", "4242424242424242", "Test Person", "12/" + (now().getYear() % 100 + 2), "123", null);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> bookingService.pay(user, ref, card));
        }
        AtomicInteger refused = new AtomicInteger();
        assertThat(runTogether(tasks, refused)).isEqualTo(1);
        assertThat(refused.get()).isEqualTo(9);
        assertThat(countRows("SELECT COUNT(*) FROM payments WHERE kind='CHARGE' AND status='SUCCEEDED'")).isEqualTo(1);
    }

    @Test
    void databaseCheckConstraintIsTheFinalSafetyNet() {
        long eventId = createUpcomingEvent("Constraint", 5, "100");
        // even a buggy direct UPDATE cannot push tickets_sold above capacity
        try {
            db.query(conn -> {
                try (var st = conn.createStatement()) {
                    return st.executeUpdate("UPDATE events SET tickets_sold = 6 WHERE id = " + eventId);
                }
            });
            org.junit.jupiter.api.Assertions.fail("CHECK constraint should have rejected the update");
        } catch (BookMatrixException e) {
            assertThat(e.code()).isEqualTo("CONSTRAINT_VIOLATION");
        }
        assertThat(ticketsSold(eventId)).isZero();
    }
}
