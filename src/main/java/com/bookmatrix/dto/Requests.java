package com.bookmatrix.dto;

import java.math.BigDecimal;

/**
 * All JSON request bodies, as small immutable records.
 * Fields that a client must not control (id, role on self-registration, tickets_sold...)
 * simply do not exist here, so extra JSON keys like "role":"ADMIN" are ignored.
 */
public final class Requests {

    private Requests() {
    }

    public record Register(String fullName, String email, String password, String confirmPassword) { }

    public record Login(String email, String password) { }

    public record ProfileUpdate(String fullName, String phone) { }

    public record PasswordChange(String currentPassword, String newPassword, String confirmPassword) { }

    /** User must re-enter the password AND type DELETE to close the account. */
    public record AccountDelete(String password, String confirmation) { }

    /** quantity for general admission; seats (e.g. ["A1","A2"]) for reserved seating. */
    public record CreateBooking(Long eventId, Integer quantity, java.util.List<String> seats, String requestKey) {
        public CreateBooking(Long eventId, Integer quantity, String requestKey) {
            this(eventId, quantity, null, requestKey);
        }
    }

    /** Payment details for the demo gateway: method CARD (number, name, expiry MM/YY, cvv) or UPI (upiId). */
    public record Pay(String method, String cardNumber, String cardName, String expiry, String cvv, String upiId) { }

    public record CancelBooking(String reason) { }

    public record EventForm(String title, String description, Integer categoryId, String venueName, String city,
                            String venueAddress, String startsAt, String endsAt, BigDecimal ticketPrice,
                            Integer capacity, String imageUrl, Boolean featured,
                            String seatingType, Integer seatRows, Integer seatsPerRow) {
        /** General-admission form (used by the CSV import and older callers). */
        public EventForm(String title, String description, Integer categoryId, String venueName, String city,
                         String venueAddress, String startsAt, String endsAt, BigDecimal ticketPrice,
                         Integer capacity, String imageUrl, Boolean featured) {
            this(title, description, categoryId, venueName, city, venueAddress, startsAt, endsAt, ticketPrice,
                    capacity, imageUrl, featured, null, null, null);
        }
    }

    public record CancelEvent(String reason) { }

    public record Featured(Boolean featured) { }

    public record AdminUserUpdate(String fullName, String phone) { }

    public record RoleChange(String role) { }

    public record NewCategory(String name, String description) { }
}
