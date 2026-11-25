package com.bookmatrix.exception;

public class BookingNotFoundException extends NotFoundException {

    public BookingNotFoundException(String reference) {
        super("Booking " + reference + " was not found.");
    }

    @Override public String code() { return "BOOKING_NOT_FOUND"; }
}
