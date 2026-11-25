package com.bookmatrix.demo;

import com.bookmatrix.demo.BookingRequestQueue.BookingRequest;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Producers submit booking requests, consumers process them, through a shared bounded queue. */
public class ProducerConsumerDemo implements ConceptDemo {

    @Override public String name() { return "producer-consumer"; }
    @Override public String title() { return "Producer-consumer booking queue (wait / notify / notifyAll)"; }
    @Override public String topics() { return "Producer-consumer, Shared queue, wait(), notify(), notifyAll(), Guarded blocks, Safe termination"; }

    /** Summary returned for tests: how much was produced/processed and whether all threads ended. */
    public record Summary(int produced, int processed, int ticketsSold, int rejected, boolean allThreadsFinished) { }

    @Override
    public void run(DemoContext ctx) throws InterruptedException {
        PrintStream out = ctx.out();
        section(out, "2 producers (web servers) -> queue(capacity 3) -> 2 consumers (booking workers)");
        Summary s = simulate(2, 5, 2, 12, out);
        out.println();
        out.println("produced=" + s.produced() + " processed=" + s.processed() + " ticketsSold=" + s.ticketsSold()
                + " rejected(sold out)=" + s.rejected() + " allThreadsFinished=" + s.allThreadsFinished());

        section(out, "notify() vs notifyAll()");
        Object bell = new Object();
        boolean[] ready = {false};
        Thread waiter = new Thread(() -> {
            synchronized (bell) {
                while (!ready[0]) {            // guarded block
                    try {
                        bell.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                out.println("  waiter: notified, ticket is ready");
            }
        });
        waiter.start();
        Thread.sleep(30);
        synchronized (bell) {
            ready[0] = true;
            bell.notify();                     // wakes ONE waiting thread - fine when exactly one waits
        }
        waiter.join();
        out.println("notify() wakes one waiting thread; the queue uses notifyAll() because producers AND");
        out.println("consumers wait on the same object, and waking the wrong one could stall everything.");
    }

    /** Runs the full simulation; also used by the unit test. */
    public static Summary simulate(int producers, int requestsEach, int consumers, int ticketsAvailable, PrintStream out)
            throws InterruptedException {
        BookingRequestQueue queue = new BookingRequestQueue(3);
        AtomicInteger produced = new AtomicInteger();
        AtomicInteger processed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        int[] remaining = {ticketsAvailable};
        Object inventoryLock = new Object();
        AtomicInteger ids = new AtomicInteger();

        List<Thread> producerThreads = new ArrayList<>();
        for (int p = 1; p <= producers; p++) {
            Thread t = new Thread(() -> {
                for (int i = 0; i < requestsEach; i++) {
                    int id = ids.incrementAndGet();
                    try {
                        queue.put(new BookingRequest(id, Thread.currentThread().getName(), 1 + (id % 2)));
                        produced.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }, "producer-" + p);
            producerThreads.add(t);
        }
        List<Thread> consumerThreads = new ArrayList<>();
        for (int c = 1; c <= consumers; c++) {
            Thread t = new Thread(() -> {
                try {
                    BookingRequest r;
                    while ((r = queue.take()) != null) {
                        boolean ok;
                        synchronized (inventoryLock) {             // critical section on shared inventory
                            ok = remaining[0] >= r.quantity();
                            if (ok) {
                                remaining[0] -= r.quantity();
                            }
                        }
                        if (!ok) {
                            rejected.incrementAndGet();
                        }
                        processed.incrementAndGet();
                        if (out != null) {
                            out.printf("  %-11s handled request #%-2d from %-10s x%d -> %s%n",
                                    Thread.currentThread().getName(), r.id(), r.customer(), r.quantity(),
                                    ok ? "CONFIRMED" : "SOLD OUT");
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "consumer-" + c);
            consumerThreads.add(t);
        }
        consumerThreads.forEach(Thread::start);
        producerThreads.forEach(Thread::start);
        for (Thread t : producerThreads) {
            t.join();
        }
        queue.close();                                   // signal: no more work is coming
        boolean finished = true;
        for (Thread t : consumerThreads) {
            t.join(5000);
            finished &= !t.isAlive();
        }
        return new Summary(produced.get(), processed.get(), ticketsAvailable - remaining[0], rejected.get(), finished);
    }
}
