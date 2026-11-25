package com.bookmatrix.demo;

import java.util.LinkedList;
import java.util.Queue;

/**
 * A bounded queue shared by producer and consumer threads, built with wait()/notifyAll().
 *
 * Guarded block pattern:  synchronized (this) { while (!condition) wait(); ... notifyAll(); }
 *  - "while", not "if": after waking up the condition is checked again (spurious wake-ups,
 *    or another thread got there first).
 *  - wait() releases the lock while sleeping, so there is no busy waiting.
 */
public class BookingRequestQueue {

    public record BookingRequest(int id, String customer, int quantity) { }

    private final Queue<BookingRequest> queue = new LinkedList<>();
    private final int capacity;
    private boolean closed = false;

    public BookingRequestQueue(int capacity) {
        this.capacity = capacity;
    }

    /** Producer side: blocks while the queue is full. */
    public synchronized void put(BookingRequest request) throws InterruptedException {
        while (queue.size() == capacity && !closed) {
            wait();
        }
        if (closed) {
            throw new IllegalStateException("Queue is closed");
        }
        queue.add(request);
        notifyAll();            // wake consumers waiting for work
    }

    /** Consumer side: blocks while empty. Returns null once closed and drained, so consumers can stop. */
    public synchronized BookingRequest take() throws InterruptedException {
        while (queue.isEmpty() && !closed) {
            wait();
        }
        if (queue.isEmpty()) {
            return null;        // closed and nothing left -> time to finish
        }
        BookingRequest next = queue.poll();
        notifyAll();            // wake producers waiting for space
        return next;
    }

    /** No more requests will come. Wakes every waiting thread so all can finish cleanly. */
    public synchronized void close() {
        closed = true;
        notifyAll();
    }

    public synchronized int size() {
        return queue.size();
    }
}
