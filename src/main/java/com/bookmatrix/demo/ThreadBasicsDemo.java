package com.bookmatrix.demo;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/** Threads: creating, start vs run, sleep, join, interrupt, lifecycle, daemon, priority, sequential vs concurrent. */
public class ThreadBasicsDemo implements ConceptDemo {

    @Override public String name() { return "threads"; }
    @Override public String title() { return "Thread basics and the thread lifecycle"; }
    @Override public String topics() { return "Process, Thread, Multiprogramming, Multithreading, Concurrency, Parallelism, Thread, Runnable, start(), run(), sleep(), join(), interrupt(), Lifecycle, Daemon, Priorities, Sequential vs concurrent"; }

    /** Way 1: extend Thread and override run(). */
    static class ReminderThread extends Thread {
        private final PrintStream out;

        ReminderThread(PrintStream out) {
            super("reminder-thread");
            this.out = out;
        }

        @Override
        public void run() {
            out.println("  [" + Thread.currentThread().getName() + "] sending booking reminder emails");
        }
    }

    @Override
    public void run(DemoContext ctx) throws Exception {
        PrintStream out = ctx.out();

        section(out, "Process vs thread");
        out.println("This JVM is ONE process (pid " + ProcessHandle.current().pid() + ") with many threads.");
        out.println("Current thread: " + Thread.currentThread().getName() + ", CPU cores: "
                + Runtime.getRuntime().availableProcessors());
        out.println("Multiprogramming = several programs (processes) share the CPU; multithreading = one");
        out.println("program runs several threads that share its memory.");

        section(out, "Creating threads: extends Thread / implements Runnable");
        Thread t1 = new ReminderThread(out);
        Runnable task = () -> out.println("  [" + Thread.currentThread().getName() + "] recalculating availability");
        Thread t2 = new Thread(task, "runnable-thread");          // Way 2: a Runnable (here a lambda)
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        section(out, "start() vs run()");
        Thread worker = new Thread(task, "new-worker");
        out.println("worker.run()  -> runs in the CALLER's thread:");
        worker.run();
        out.println("worker.start() -> creates a NEW thread which then calls run():");
        worker.start();
        worker.join();

        section(out, "sleep() and join()");
        Thread sleeper = new Thread(() -> {
            try {
                Thread.sleep(150);
                out.println("  sleeper woke up after 150 ms");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "sleeper");
        long start = System.currentTimeMillis();
        sleeper.start();
        sleeper.join();     // main waits here until sleeper finishes
        out.println("join() waited ~" + (System.currentTimeMillis() - start) + " ms for the sleeper");

        section(out, "interrupt()");
        Thread longTask = new Thread(() -> {
            try {
                Thread.sleep(10_000);
                out.println("  never printed");
            } catch (InterruptedException e) {
                out.println("  long task: InterruptedException caught, cleaning up and stopping");
                Thread.currentThread().interrupt();   // restore the flag for code further up
            }
        }, "long-task");
        longTask.start();
        Thread.sleep(50);
        longTask.interrupt();
        longTask.join();

        section(out, "Thread lifecycle states");
        Object lock = new Object();
        Thread life = new Thread(() -> {
            long until = System.currentTimeMillis() + 120;
            while (System.currentTimeMillis() < until) { /* busy -> RUNNABLE */ }
            try {
                Thread.sleep(150);                       // -> TIMED_WAITING
                synchronized (lock) {                    // -> BLOCKED while main holds the lock
                    lock.wait();                         // -> WAITING until notified
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "lifecycle");
        List<Thread.State> seen = new ArrayList<>();
        seen.add(life.getState());                       // NEW
        synchronized (lock) {
            life.start();
            record(seen, life, Thread.State.RUNNABLE);
            record(seen, life, Thread.State.TIMED_WAITING);
            record(seen, life, Thread.State.BLOCKED);    // it wants "lock", which main still holds
        }
        record(seen, life, Thread.State.WAITING);
        synchronized (lock) {
            lock.notifyAll();
        }
        life.join();
        seen.add(life.getState());                       // TERMINATED
        out.println("Observed: " + seen);

        section(out, "Daemon threads and priorities");
        Thread daemon = new Thread(() -> { }, "cache-cleaner");
        daemon.setDaemon(true);
        out.println("cache-cleaner daemon? " + daemon.isDaemon() + " -> the JVM will not wait for it to finish");
        Thread high = new Thread(() -> { }, "high");
        high.setPriority(Thread.MAX_PRIORITY);
        Thread low = new Thread(() -> { }, "low");
        low.setPriority(Thread.MIN_PRIORITY);
        out.println("priorities: high=" + high.getPriority() + " low=" + low.getPriority()
                + " (only a HINT to the scheduler; never rely on it for order)");

        section(out, "Sequential vs concurrent execution");
        Runnable slowTask = () -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        long seqStart = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            slowTask.run();
        }
        long sequential = System.currentTimeMillis() - seqStart;
        long conStart = System.currentTimeMillis();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Thread t = new Thread(slowTask);
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }
        long concurrent = System.currentTimeMillis() - conStart;
        out.println("3 x 100 ms tasks: sequential ~" + sequential + " ms, concurrent ~" + concurrent + " ms");
        long sum = IntStream.rangeClosed(1, 1_000_000).parallel().asLongStream().sum();
        out.println("Parallelism: a parallel stream split a sum over the CPU cores -> " + sum);
    }

    /** Waits (max 2 s) until the thread reaches the expected state, then records it. */
    private static void record(List<Thread.State> seen, Thread t, Thread.State expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (t.getState() != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(1);
        }
        seen.add(t.getState());
    }
}
