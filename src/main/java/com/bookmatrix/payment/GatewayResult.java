package com.bookmatrix.payment;

/** Outcome reported by a payment gateway. */
public record GatewayResult(boolean approved, String gatewayRef, String method, String cardBrand, String cardLast4,
                            String upiHandle, String declineReason) {
}
