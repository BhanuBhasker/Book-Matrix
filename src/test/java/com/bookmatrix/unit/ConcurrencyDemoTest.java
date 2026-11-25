package com.bookmatrix.unit;

import com.bookmatrix.demo.BookingRequestQueue;
import com.bookmatrix.demo.ProducerConsumerDemo;
import com.bookmatrix.demo.RaceConditionDemo;
import com.bookmatrix.demo.RaceConditionDemo.RaceResult;
import com.bookmatrix.demo.SeatMap;
import com.bookmatrix.demo.TicketCounters;
import com.bookmatrix.security.LoginAttemptTracker;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Multithreading demonstrations, the producer-consumer queue and the 2-D seat map. */
class ConcurrencyDemoTest {

    @Test
    void unsafeCounterOversellsUnderARace() throws Exception {
        boolean oversold = false;
        for (int attempt = 0; attempt < 5 && !oversold; attempt++) {
            oversold = RaceConditionDemo.race(new TicketCounters.UnsafeCounter(1, 30), 4).oversold();
        }
        assertThat(oversold).as("the unsafe check-then-act version should oversell at least once").isTrue();
    }

    @RepeatedTest(5)
    void safeCountersNeverOversell() throws Exception {
        for (TicketCounters.TicketCounter counter : new TicketCounters.TicketCounter[]{
                new TicketCounters.SynchronizedMethodCounter(3, 2),
                new TicketCounters.SynchronizedBlockCounter(3, 2),
                new TicketCounters.AtomicCounter(3)}) {
            RaceResult r = RaceConditionDemo.race(counter, 20);
            assertThat(r.successfulBookings()).isEqualTo(3);
            assertThat(r.ticketsLeft()).isZero();
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void producerConsumerProcessesEverythingAndTerminates() throws Exception {
        ProducerConsumerDemo.Summary s = ProducerConsumerDemo.simulate(3, 20, 3, 25, null);
        assertThat(s.produced()).isEqualTo(60);
        assertThat(s.processed()).isEqualTo(60);
        assertThat(s.ticketsSold()).isLessThanOrEqualTo(25);
        assertThat(s.allThreadsFinished()).isTrue();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void closedQueueWakesWaitingConsumers() throws Exception {
        BookingRequestQueue queue = new BookingRequestQueue(2);
        Thread consumer = new Thread(() -> {
            try {
                assertThat(queue.take()).isNull();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        consumer.start();
        Thread.sleep(50);
        queue.close();
        consumer.join(2000);
        assertThat(consumer.isAlive()).isFalse();
        assertThatThrownBy(() -> queue.put(new BookingRequestQueue.BookingRequest(1, "x", 1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void seatMapArrayOperations() {
        SeatMap map = new SeatMap(3, 5);
        assertThat(map.countFree()).isEqualTo(15);
        assertThat(map.book(0, 1)).isTrue();
        assertThat(map.book(0, 1)).isFalse();
        assertThat(map.findAdjacent(4)).containsExactly(1, 0);
        assertThat(map.render()).contains("A   . X");
        assertThatThrownBy(() -> map.book(3, 0)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> new SeatMap(0, 5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loginTrackerLocksAfterFiveFailures() {
        LoginAttemptTracker tracker = new LoginAttemptTracker();
        for (int i = 0; i < 4; i++) {
            tracker.recordFailure("x@y.com");
        }
        assertThat(tracker.isLocked("x@y.com")).isFalse();
        tracker.recordFailure("x@y.com");
        assertThat(tracker.isLocked("x@y.com")).isTrue();
        tracker.recordSuccess("x@y.com");
        assertThat(tracker.isLocked("x@y.com")).isFalse();
    }
}
