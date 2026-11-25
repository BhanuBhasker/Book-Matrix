package com.bookmatrix.util;

import com.bookmatrix.model.Booking;
import com.bookmatrix.model.BookingStatus;
import com.bookmatrix.model.Payment;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Builds the plain-text booking receipt and confirmation message with StringBuilder.
 * StringBuilder is mutable: append() changes the same object instead of creating a
 * new String each time (which "+" in a loop would do).
 */
public final class ReceiptBuilder {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE, d MMM yyyy 'at' h:mm a");
    private static final int WIDTH = 46;

    private ReceiptBuilder() {
    }

    public static String receipt(Booking booking) {
        return receipt(booking, List.of());
    }

    /** Receipt including seat numbers and the payment/refund history (card shown as last 4 digits only). */
    public static String receipt(Booking booking, List<Payment> payments) {
        StringBuilder sb = new StringBuilder();
        line(sb, '=');
        center(sb, "BOOKMATRIX");
        center(sb, booking.getStatus() == BookingStatus.CANCELLED ? "CANCELLED BOOKING" : "BOOKING RECEIPT");
        line(sb, '=');
        row(sb, "Reference", booking.getReference());
        row(sb, "Status", switch (booking.getStatus()) {
            case PENDING_PAYMENT -> "AWAITING PAYMENT";
            default -> booking.getStatus().name();
        });
        row(sb, "Booked on", booking.getBookedAt().format(WHEN));
        line(sb, '-');
        sb.append(booking.getEventTitle()).append('\n');
        sb.append(booking.getVenueName()).append(", ").append(booking.getCity()).append('\n');
        sb.append(booking.getEventStartsAt().format(WHEN)).append('\n');
        line(sb, '-');
        row(sb, "Tickets", String.valueOf(booking.getQuantity()));
        if (booking.getSeatLabels() != null) {
            row(sb, "Seats", booking.getSeatLabels());
        }
        row(sb, "Price per ticket", Money.format(booking.getUnitPrice()));
        row(sb, booking.getPaidAt() != null ? "Total paid" : "Total due", Money.format(booking.getTotalAmount()));
        for (Payment p : payments) {
            if (p.status() == Payment.Status.SUCCEEDED) {
                row(sb, p.kind() == Payment.Kind.REFUND ? "Refunded to" : "Paid with", p.describeMethod());
                row(sb, "Transaction", p.gatewayRef());
            }
        }
        if (booking.getStatus() == BookingStatus.CANCELLED && booking.getCancelledAt() != null) {
            line(sb, '-');
            row(sb, "Cancelled on", booking.getCancelledAt().format(WHEN));
            if (booking.getCancellationReason() != null) {
                row(sb, "Reason", booking.getCancellationReason());
            }
            row(sb, "Refund", Money.format(booking.getTotalAmount()));
        }
        line(sb, '=');
        center(sb, "Show this reference at the entrance.");
        return sb.toString();
    }

    public static String confirmationMessage(Booking booking) {
        return new StringBuilder()
                .append("You're going! ")
                .append(booking.getQuantity()).append(booking.getQuantity() == 1 ? " ticket" : " tickets")
                .append(" for ").append(booking.getEventTitle())
                .append(booking.getQuantity() == 1 ? " is confirmed. Reference " : " are confirmed. Reference ").append(booking.getReference()).append('.')
                .toString();
    }

    private static void line(StringBuilder sb, char c) {
        sb.append(String.valueOf(c).repeat(WIDTH)).append('\n');
    }

    private static void center(StringBuilder sb, String text) {
        int pad = Math.max(0, (WIDTH - text.length()) / 2);
        sb.append(" ".repeat(pad)).append(text).append('\n');
    }

    private static void row(StringBuilder sb, String label, String value) {
        sb.append(String.format("%-18s%28s%n", label, value));
    }
}
