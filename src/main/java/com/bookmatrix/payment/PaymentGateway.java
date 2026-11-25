package com.bookmatrix.payment;

/**
 * Abstraction over a payment provider. BookMatrix ships with DemoPaymentGateway; a real provider
 * (Razorpay, Stripe...) would be another implementation of this same interface, and BookingService
 * would not need to change — that is the point of programming to an interface.
 */
public interface PaymentGateway {

    /** Validates and charges. Throws ValidationException for badly formed details. */
    GatewayResult charge(PaymentRequest request);

    String name();
}
