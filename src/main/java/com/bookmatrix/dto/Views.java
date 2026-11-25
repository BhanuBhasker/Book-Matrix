package com.bookmatrix.dto;

import com.bookmatrix.model.AccountStatus;
import com.bookmatrix.model.AuditEntry;
import com.bookmatrix.model.Booking;
import com.bookmatrix.model.BookingStatus;
import com.bookmatrix.model.Event;
import com.bookmatrix.model.EventDisplayStatus;
import com.bookmatrix.model.Payment;
import com.bookmatrix.model.SeatingType;
import com.bookmatrix.model.Role;
import com.bookmatrix.model.User;
import com.bookmatrix.model.UserWithStats;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * JSON response shapes. Domain objects are converted to these "views" before leaving the
 * server, so sensitive fields (like password_hash) can never be serialised by accident.
 */
public final class Views {

    private Views() {
    }

    public record UserView(long id, String fullName, String email, String phone, Role role, AccountStatus status,
                           LocalDateTime createdAt, LocalDateTime lastLoginAt) {
        public static UserView from(User u) {
            return new UserView(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getRole(), u.getStatus(),
                    u.getCreatedAt(), u.getLastLoginAt());
        }
    }

    public record AdminUserView(UserView user, long bookingCount, long ticketsHeld, BigDecimal totalSpent) {
        public static AdminUserView from(UserWithStats s) {
            return new AdminUserView(UserView.from(s.user()), s.bookingCount(), s.ticketsHeld(), s.totalSpent());
        }
    }

    public record SessionInfo(UserView user, String csrfToken) { }

    public record EventView(long id, String title, String description, int categoryId, String categoryName,
                            String venueName, String city, String venueAddress, LocalDateTime startsAt,
                            LocalDateTime endsAt, BigDecimal ticketPrice, int capacity, int ticketsSold,
                            int availableTickets, String imageUrl, boolean featured, String status,
                            EventDisplayStatus displayStatus, String displayStatusLabel, boolean bookable,
                            String bookingBlockReason, int maxPerBooking, boolean archived,
                            LocalDateTime cancelledAt, SeatingType seatingType, Integer seatRows, Integer seatsPerRow) {
        public static EventView from(Event e, LocalDateTime now, int maxPerBooking) {
            EventDisplayStatus display = e.getDisplayStatus(now);
            return new EventView(e.getId(), e.getTitle(), e.getDescription(), e.getCategoryId(), e.getCategoryName(),
                    e.getVenueName(), e.getCity(), e.getVenueAddress(), e.getStartsAt(), e.getEndsAt(),
                    e.getTicketPrice(), e.getCapacity(), e.getTicketsSold(), e.getAvailableTickets(), e.getImageUrl(),
                    e.isFeatured(), e.getStatus().name(), display, display.getLabel(), e.isBookable(now),
                    e.getBookingBlockReason(now).orElse(null),
                    Math.min(maxPerBooking, Math.max(0, e.getAvailableTickets())), e.isArchived(), e.getCancelledAt(),
                    e.getSeatingType(), e.getSeatRows(), e.getSeatsPerRow());
        }
    }

    public record BookingView(long id, String reference, long eventId, String eventTitle, LocalDateTime eventStartsAt,
                              LocalDateTime eventEndsAt, String eventStatus, String venueName, String city,
                              int quantity, String seatLabels, BigDecimal unitPrice, BigDecimal totalAmount,
                              BookingStatus status, LocalDateTime bookedAt, LocalDateTime holdExpiresAt,
                              LocalDateTime paidAt, boolean payable, LocalDateTime cancelledAt, String cancelledByName,
                              String cancellationReason, boolean cancellable, String cancellationBlockReason,
                              boolean eventPast, Long userId, String userName, String userEmail,
                              List<PaymentView> payments) {

        /** forAdmin decides whether the customer's name/email are included. */
        public static BookingView from(Booking b, LocalDateTime now, int cutoffHours, boolean forAdmin) {
            String block = b.getCancellationBlockReason(now, cutoffHours).orElse(null);
            return new BookingView(b.getId(), b.getReference(), b.getEventId(), b.getEventTitle(), b.getEventStartsAt(),
                    b.getEventEndsAt(), b.getEventStatus().name(), b.getVenueName(), b.getCity(), b.getQuantity(),
                    b.getSeatLabels(), b.getUnitPrice(), b.getTotalAmount(), b.getStatus(), b.getBookedAt(),
                    b.getHoldExpiresAt(), b.getPaidAt(), b.isPayable(now), b.getCancelledAt(),
                    b.getCancelledByName(), b.getCancellationReason(), block == null, block,
                    b.getEventEndsAt() != null && !now.isBefore(b.getEventEndsAt()),
                    forAdmin ? b.getUserId() : null, forAdmin ? b.getUserName() : null, forAdmin ? b.getUserEmail() : null,
                    null);
        }

        /** Same booking with its payment history attached (detail screens only). */
        public BookingView withPayments(List<PaymentView> list) {
            return new BookingView(id, reference, eventId, eventTitle, eventStartsAt, eventEndsAt, eventStatus, venueName, city,
                    quantity, seatLabels, unitPrice, totalAmount, status, bookedAt, holdExpiresAt, paidAt, payable, cancelledAt,
                    cancelledByName, cancellationReason, cancellable, cancellationBlockReason, eventPast, userId, userName,
                    userEmail, list);
        }
    }

    /** A payment as shown to people: never contains more than the last 4 card digits. */
    public record PaymentView(long id, String bookingReference, Payment.Kind kind, Payment.Status status, BigDecimal amount,
                              Payment.Method method, String methodLabel, String gatewayRef, String failureReason,
                              LocalDateTime createdAt) {
        public static PaymentView from(Payment p) {
            return new PaymentView(p.id(), p.bookingReference(), p.kind(), p.status(), p.amount(), p.method(),
                    p.describeMethod(), p.gatewayRef(), p.failureReason(), p.createdAt());
        }
    }

    public record PaymentResult(BookingView booking, PaymentView payment, String message) { }

    /** Seat map of a RESERVED event: which seats exist and which are already taken (held or sold). */
    public record SeatMapView(long eventId, int rows, int seatsPerRow, List<String> rowLabels, List<String> taken,
                              int available, int maxPerBooking) { }

    public record BookingConfirmation(BookingView booking, String message, boolean duplicate) { }

    public record CategoryView(int id, String name, String slug, String description, long upcomingEvents) { }

    public record HomeView(List<EventView> featured, List<EventView> upcoming, List<CategoryView> categories,
                           List<String> cities, long upcomingCount) { }

    public record AccountClosed(String outcome, int bookingsCancelled, String message) { }

    public record EventCancelled(EventView event, int bookingsCancelled, String message) { }

    public record DeleteOutcome(String outcome, String message) { }

    public record AuditView(long id, String actorName, String action, String entityType, Long entityId,
                            String details, LocalDateTime createdAt) {
        public static AuditView from(AuditEntry a) {
            return new AuditView(a.id(), a.actorName(), a.action(), a.entityType(), a.entityId(), a.details(),
                    a.createdAt());
        }
    }

    public record InventoryView(long eventId, String title, String city, LocalDateTime startsAt, String status,
                                boolean archived, int capacity, int ticketsSold, int available,
                                long confirmedTickets, long heldTickets, Long seatsTaken, BigDecimal revenue,
                                boolean consistent, int percentSold) { }

    public record TopEvent(long eventId, String title, int ticketsSold, int capacity, BigDecimal revenue) { }

    public record Dashboard(Map<String, Long> counts, BigDecimal revenue, BigDecimal refunded,
                            BigDecimal averageBookingValue, List<TopEvent> topEvents,
                            Map<String, Integer> ticketsByCategory, List<BookingView> recentBookings,
                            List<AuditView> recentActivity, int inconsistentEvents, LocalDateTime generatedAt) { }

    public record ImportRowError(int line, String message) { }

    public record ImportReport(int rowsRead, int imported, int skipped, List<ImportRowError> errors) { }

    public record BackupFile(String name, long bytes) { }

    public record BackupReport(String folder, List<BackupFile> files, LocalDateTime createdAt) { }

    public record DemoInfo(String name, String title, String topics) { }

    public record DemoOutput(String name, String title, String output, long millis, boolean success) { }
}
