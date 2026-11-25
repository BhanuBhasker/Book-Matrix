package com.bookmatrix.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One row of the payments table: a charge attempt or a refund. Never holds a full card number. */
public record Payment(long id, long bookingId, String bookingReference, Kind kind, Status status, BigDecimal amount,
                      Method method, String cardBrand, String cardLast4, String upiHandle, String gatewayRef,
                      String failureReason, LocalDateTime createdAt) {

    public enum Kind { CHARGE, REFUND }

    public enum Status { SUCCEEDED, FAILED }

    public enum Method { CARD, UPI }

    /** e.g. "Visa •••• 4242" or "UPI asha@okbank". */
    public String describeMethod() {
        return method == Method.CARD ? cardBrand + " •••• " + cardLast4 : "UPI " + upiHandle;
    }
}
