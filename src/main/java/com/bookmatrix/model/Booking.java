package com.bookmatrix.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * A user's purchase of tickets for one event.
 * Besides its own columns it carries a few joined fields (event title, user name...)
 * so the UI can show a booking without extra queries.
 */
public class Booking extends BaseEntity {

    private String reference;
    private long userId;
    private long eventId;
    private int quantity;
    private String seatLabels;              // e.g. "A1, A2" for reserved seating, else null
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private BookingStatus status = BookingStatus.CONFIRMED;
    private String requestKey;
    private LocalDateTime bookedAt;
    private LocalDateTime holdExpiresAt;    // pending bookings must be paid before this
    private LocalDateTime paidAt;
    private LocalDateTime cancelledAt;
    private Long cancelledBy;
    private String cancelledByName;
    private String cancellationReason;

    // joined, read-only information
    private String eventTitle;
    private LocalDateTime eventStartsAt;
    private LocalDateTime eventEndsAt;
    private EventStatus eventStatus;
    private String venueName;
    private String city;
    private String userName;
    private String userEmail;

    @Override
    public String describe() {
        return reference + " x" + quantity + " " + status;
    }

    /** Price maths lives in one static method so it is easy to unit test. */
    public static BigDecimal calculateTotal(BigDecimal unitPrice, int quantity) {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    /**
     * Cancellation policy: only CONFIRMED bookings, only for events that are not cancelled,
     * and only up to {@code cutoffHours} hours before the event starts.
     */
    public Optional<String> getCancellationBlockReason(LocalDateTime now, int cutoffHours) {
        if (status == BookingStatus.CANCELLED) {
            return Optional.of("This booking is already cancelled.");
        }
        if (status == BookingStatus.EXPIRED) {
            return Optional.of("This booking expired because it was not paid in time.");
        }
        if (status == BookingStatus.PENDING_PAYMENT) {
            return eventStartsAt != null && !now.isBefore(eventStartsAt)
                    ? Optional.of("This event has already started.") : Optional.empty();   // releasing a hold is always fine
        }
        if (eventStartsAt != null && !now.isBefore(eventStartsAt.minusHours(cutoffHours))) {
            return Optional.of("Bookings can only be cancelled up to " + cutoffHours
                    + " hours before the event starts.");
        }
        return Optional.empty();
    }

    /** A pending booking that can still be paid right now. */
    public boolean isPayable(LocalDateTime now) {
        return status == BookingStatus.PENDING_PAYMENT && holdExpiresAt != null && now.isBefore(holdExpiresAt);
    }

    public String getSeatLabels() { return seatLabels; }
    public void setSeatLabels(String seatLabels) { this.seatLabels = seatLabels; }
    public LocalDateTime getHoldExpiresAt() { return holdExpiresAt; }
    public void setHoldExpiresAt(LocalDateTime holdExpiresAt) { this.holdExpiresAt = holdExpiresAt; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }
    public long getEventId() { return eventId; }
    public void setEventId(long eventId) { this.eventId = eventId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
    public LocalDateTime getBookedAt() { return bookedAt; }
    public void setBookedAt(LocalDateTime bookedAt) { this.bookedAt = bookedAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public Long getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(Long cancelledBy) { this.cancelledBy = cancelledBy; }
    public String getCancelledByName() { return cancelledByName; }
    public void setCancelledByName(String cancelledByName) { this.cancelledByName = cancelledByName; }
    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }
    public String getEventTitle() { return eventTitle; }
    public void setEventTitle(String eventTitle) { this.eventTitle = eventTitle; }
    public LocalDateTime getEventStartsAt() { return eventStartsAt; }
    public void setEventStartsAt(LocalDateTime eventStartsAt) { this.eventStartsAt = eventStartsAt; }
    public LocalDateTime getEventEndsAt() { return eventEndsAt; }
    public void setEventEndsAt(LocalDateTime eventEndsAt) { this.eventEndsAt = eventEndsAt; }
    public EventStatus getEventStatus() { return eventStatus; }
    public void setEventStatus(EventStatus eventStatus) { this.eventStatus = eventStatus; }
    public String getVenueName() { return venueName; }
    public void setVenueName(String venueName) { this.venueName = venueName; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
}
