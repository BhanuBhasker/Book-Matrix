package com.bookmatrix.exception;

/** Not enough tickets left. A special case of an invalid booking. */
public class InsufficientTicketsException extends InvalidBookingException {

    private final int requested;
    private final int available;

    public InsufficientTicketsException(int requested, int available) {
        super(available <= 0
                ? "Sorry, this event is sold out."
                : "Only " + available + " ticket(s) left, you asked for " + requested + ".");
        this.requested = requested;
        this.available = available;
    }

    public int getRequested() { return requested; }
    public int getAvailable() { return available; }

    @Override public String code() { return "INSUFFICIENT_TICKETS"; }
}
