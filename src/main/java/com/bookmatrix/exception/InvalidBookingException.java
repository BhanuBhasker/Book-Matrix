package com.bookmatrix.exception;

import org.springframework.http.HttpStatus;

/** The booking/cancellation breaks a business rule (event started, cancelled, already cancelled...). */
public class InvalidBookingException extends BookMatrixException {

    public InvalidBookingException(String message) {
        super(message);
    }

    @Override public HttpStatus status() { return HttpStatus.CONFLICT; }
    @Override public String code() { return "BOOKING_NOT_ALLOWED"; }
}
