package com.bookmatrix.exception;

/** Inheritance in exceptions: a more specific kind of NotFoundException. */
public class EventNotFoundException extends NotFoundException {

    public EventNotFoundException(long eventId) {
        super("Event " + eventId + " was not found.");
    }

    @Override public String code() { return "EVENT_NOT_FOUND"; }
}
