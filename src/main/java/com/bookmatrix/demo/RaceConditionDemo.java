package com.bookmatrix.demo;

import com.bookmatrix.demo.TicketCounters.TicketCounter;

import java.io.PrintStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/** Two users try to buy the LAST ticket at the same moment. */
public class RaceConditionDemo implements ConceptDemo {

    @Override public String name() { return "race"; }
    @Override public String title() { return "Race conditions and synchronization"; }
    @Override public String topics() { return "Race conditions, Critical sections, synchronized methods, synchronized blocks, Intrinsic locks, Atomicity, Visibility, volatile"; }

    /** Result of one "last ticket" race. */
    public record RaceResult(int successfulBookings, int ticketsLeft) {
        public boolean oversold() {
            return ticketsLeft < 0 || successfulBookings > 1;
        }
    }

    /** Starts `users` threads at exactly the same moment, all trying to book from the same counter. */
    public static RaceResult race(TicketCounter counter, int users) throws InterruptedException {
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(users);
        AtomicInteger successes = new AtomicInteger();
        for (int i = 0; i < users; i++) {
            new Thread(() -> {
                try {
                    startGate.await();               // everyone waits for the starting pistol
                    if (counter.tryBook()) {
                        successes.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, "user-" + (i + 1)).start();
        }
        startGate.countDown();
        done.await();
        return new RaceResult(successes.get(), counter.available());
    }

    @Override
    public void run(DemoContext ctx) throws InterruptedException {
        PrintStream out = ctx.out();

        section(out, "1 ticket left, 2 users click 'Book' at the same time");
        report(out, "UNSAFE (no lock)", race(new TicketCounters.UnsafeCounter(1, 50), 2));
        report(out, "synchronized method", race(new TicketCounters.SynchronizedMethodCounter(1, 50), 2));
        report(out, "synchronized block", race(new TicketCounters.SynchronizedBlockCounter(1, 50), 2));
        report(out, "AtomicInteger CAS", race(new TicketCounters.AtomicCounter(1), 2));

        section(out, "Atomicity: count++ is really read + add + write");
        int[] unsafe = {0};
        AtomicInteger atomic = new AtomicInteger();
        Object lock = new Object();
        int[] locked = {0};
        Thread[] workers = new Thread[4];
        for (int w = 0; w < workers.length; w++) {
            workers[w] = new Thread(() -> {
                for (int i = 0; i < 50_000; i++) {
                    unsafe[0]++;                          // lost updates possible
                    atomic.incrementAndGet();             // atomic
                    synchronized (lock) {
                        locked[0]++;                      // protected by an intrinsic lock
                    }
                }
            });
            workers[w].start();
        }
        for (Thread w : workers) {
            w.join();
        }
        out.println("expected 200000 -> plain int: " + unsafe[0] + ", AtomicInteger: " + atomic.get()
                + ", synchronized: " + locked[0]);

        section(out, "Visibility: volatile");
        VolatileFlag flag = new VolatileFlag();
        Thread poller = new Thread(flag::spinUntilStopped, "poller");
        poller.start();
        Thread.sleep(50);
        flag.stop();
        poller.join(2000);
        out.println("Worker saw the stop signal: " + !poller.isAlive()
                + " (volatile guarantees one thread's write is visible to other threads)");

        section(out, "Java locks vs database locks");
        out.println("synchronized only protects ONE JVM. BookMatrix may run several app instances, so the");
        out.println("real booking uses the DATABASE: a transaction, SELECT ... FOR UPDATE on the event row,");
        out.println("and an atomic UPDATE ... WHERE tickets_sold + qty <= capacity (see BookingService).");
    }

    private static void report(PrintStream out, String label, RaceResult r) {
        out.printf("%-22s successful bookings=%d, tickets left=%d %s%n", label, r.successfulBookings(), r.ticketsLeft(),
                r.oversold() ? "<-- OVERSOLD!" : "(correct)");
    }

    static class VolatileFlag {
        private volatile boolean running = true;

        void spinUntilStopped() {
            while (running) {
                Thread.onSpinWait();
            }
        }

        void stop() {
            running = false;
        }
    }
}
