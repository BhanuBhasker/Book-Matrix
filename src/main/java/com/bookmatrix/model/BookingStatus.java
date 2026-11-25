package com.bookmatrix.model;

/**
 * Life of a booking:
 *   PENDING_PAYMENT --pay--> CONFIRMED --cancel--> CANCELLED (refunded)
 *   PENDING_PAYMENT --hold runs out--> EXPIRED
 *   PENDING_PAYMENT --user releases--> CANCELLED (nothing was charged)
 * Free events skip PENDING_PAYMENT and are CONFIRMED straight away.
 */
public enum BookingStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    CANCELLED,
    EXPIRED;

    /** Holds tickets/seats right now (counts against inventory). */
    public boolean holdsInventory() {
        return this == PENDING_PAYMENT || this == CONFIRMED;
    }
}
