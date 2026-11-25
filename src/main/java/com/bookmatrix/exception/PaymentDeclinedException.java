package com.bookmatrix.exception;

import org.springframework.http.HttpStatus;

/** The gateway refused the payment. HTTP 402 "Payment Required"; the booking stays on hold so the user can retry. */
public class PaymentDeclinedException extends BookMatrixException {

    public PaymentDeclinedException(String reason) {
        super(reason + " No money was taken — you can try another card or UPI.");
    }

    @Override public HttpStatus status() { return HttpStatus.PAYMENT_REQUIRED; }
    @Override public String code() { return "PAYMENT_DECLINED"; }
}
