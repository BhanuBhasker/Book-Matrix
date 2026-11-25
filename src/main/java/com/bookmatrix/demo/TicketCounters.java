package com.bookmatrix.demo;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory ticket counters used to demonstrate race conditions and their fixes.
 * The real application does NOT keep inventory in Java memory — MySQL is the source of truth
 * (see BookingService) — but the same ideas apply.
 */
public final class TicketCounters {

    private TicketCounters() {
    }

    /** Common interface so the demo can test every version the same way. */
    public interface TicketCounter {
        boolean tryBook();

        int available();
    }

    /**
     * UNSAFE: "check then act" is two separate steps. Two threads can both see 1 ticket left,
     * both pass the check, and both decrement -> oversold (available becomes -1).
     */
    public static class UnsafeCounter implements TicketCounter {
        private int available;
        private final long pauseMillis;

        public UnsafeCounter(int available, long pauseMillis) {
            this.available = available;
            this.pauseMillis = pauseMillis;
        }

        @Override
        public boolean tryBook() {
            if (available > 0) {                 // check
                pause(pauseMillis);              // widen the gap so the race shows up reliably
                available--;                     // act
                return true;
            }
            return false;
        }

        @Override
        public int available() {
            return available;
        }
    }

    /** SAFE: synchronized METHOD — the whole method is a critical section guarded by "this". */
    public static class SynchronizedMethodCounter implements TicketCounter {
        private int available;
        private final long pauseMillis;

        public SynchronizedMethodCounter(int available, long pauseMillis) {
            this.available = available;
            this.pauseMillis = pauseMillis;
        }

        @Override
        public synchronized boolean tryBook() {
            if (available > 0) {
                pause(pauseMillis);
                available--;
                return true;
            }
            return false;
        }

        @Override
        public synchronized int available() {
            return available;
        }
    }

    /** SAFE: synchronized BLOCK on a private lock object — only the critical section is locked. */
    public static class SynchronizedBlockCounter implements TicketCounter {
        private final Object lock = new Object();      // intrinsic lock (monitor)
        private int available;
        private final long pauseMillis;

        public SynchronizedBlockCounter(int available, long pauseMillis) {
            this.available = available;
            this.pauseMillis = pauseMillis;
        }

        @Override
        public boolean tryBook() {
            // work that needs no lock could go here
            synchronized (lock) {                        // critical section starts
                if (available > 0) {
                    pause(pauseMillis);
                    available--;
                    return true;
                }
                return false;
            }                                            // critical section ends, lock released
        }

        @Override
        public int available() {
            synchronized (lock) {
                return available;
            }
        }
    }

    /** SAFE without locks: compareAndSet makes "check and decrement" ONE atomic step. */
    public static class AtomicCounter implements TicketCounter {
        private final AtomicInteger available;

        public AtomicCounter(int available) {
            this.available = new AtomicInteger(available);
        }

        @Override
        public boolean tryBook() {
            while (true) {
                int current = available.get();
                if (current <= 0) {
                    return false;
                }
                if (available.compareAndSet(current, current - 1)) {
                    return true;
                }
                // another thread changed it first: read again and retry
            }
        }

        @Override
        public int available() {
            return available.get();
        }
    }

    static void pause(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
