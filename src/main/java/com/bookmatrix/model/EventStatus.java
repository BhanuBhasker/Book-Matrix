package com.bookmatrix.model;

/**
 * The only two statuses that are STORED for an event.
 * Everything else (upcoming, sold out, live, completed) is derived — see {@link EventDisplayStatus}.
 */
public enum EventStatus {
    SCHEDULED,
    CANCELLED
}
