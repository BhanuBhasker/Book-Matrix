package com.bookmatrix.payment;

import java.math.BigDecimal;

/**
 * What the browser sends to pay for a booking. The card number and CVV only live in memory
 * for the length of this request — they are never written to the database or the logs.
 */
public record PaymentRequest(String method, String cardNumber, String cardName, String expiry, String cvv,
                             String upiId, BigDecimal amount, String bookingReference) {

    /** Never print card data, even by accident (records generate toString automatically). */
    @Override
    public String toString() {
        return "PaymentRequest[method=" + method + ", booking=" + bookingReference + ", amount=" + amount + "]";
    }
}
