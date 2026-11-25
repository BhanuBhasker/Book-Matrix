package com.bookmatrix.model;

import java.math.BigDecimal;

/** A user plus booking totals, for the admin user list. */
public record UserWithStats(User user, long bookingCount, long ticketsHeld, BigDecimal totalSpent) {
}
