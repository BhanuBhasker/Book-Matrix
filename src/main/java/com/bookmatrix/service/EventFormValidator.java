package com.bookmatrix.service;

import com.bookmatrix.dto.Requests;
import com.bookmatrix.model.Event;
import com.bookmatrix.model.SeatingType;
import com.bookmatrix.util.Money;
import com.bookmatrix.util.TextUtils;
import com.bookmatrix.validation.Validator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * Validation rules for creating/editing an event. Shared by the admin form (EventService)
 * and the CSV import (DataTransferService) so both follow exactly the same rules.
 */
@Component
public class EventFormValidator {

    private static final BigDecimal MAX_PRICE = new BigDecimal("1000000");
    public static final int MAX_ROWS = 26;
    public static final int MAX_SEATS_PER_ROW = 30;

    /** Validates the admin form and builds an Event object (not yet saved). */
    public Event validate(Requests.EventForm form, Event existing, LocalDateTime now) {
        String title = TextUtils.clean(form.title());
        String description = form.description() == null ? null : form.description().strip();
        String venueName = TextUtils.clean(form.venueName());
        String city = TextUtils.clean(form.city());
        String address = TextUtils.clean(form.venueAddress());
        String imageUrl = TextUtils.clean(form.imageUrl());

        Validator v = new Validator()
                .required("title", title, "Enter a title.").length("title", title, 3, 150)
                .required("description", description, "Enter a description.").length("description", description, 10, 5000)
                .required("categoryId", form.categoryId(), "Choose a category.")
                .required("venueName", venueName, "Enter the venue name.").length("venueName", venueName, 2, 120)
                .required("city", city, "Enter the city.").length("city", city, 2, 80)
                .length("venueAddress", address, 0, 255)
                .required("ticketPrice", form.ticketPrice(), "Enter a ticket price (0 for free events).")
                .money("ticketPrice", form.ticketPrice(), MAX_PRICE);

        // Seating: GENERAL needs a capacity; RESERVED needs a layout and capacity = rows x seats per row.
        SeatingType seating = SeatingType.GENERAL;
        if (!TextUtils.isBlank(form.seatingType())) {
            try {
                seating = SeatingType.valueOf(form.seatingType().trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                v.check(false, "seatingType", "Seating must be GENERAL or RESERVED.");
            }
        }
        if (seating == SeatingType.RESERVED) {
            v.required("seatRows", form.seatRows(), "Enter the number of rows.")
                    .range("seatRows", form.seatRows(), 1, MAX_ROWS, "Rows must be between 1 and " + MAX_ROWS + " (A–Z).")
                    .required("seatsPerRow", form.seatsPerRow(), "Enter the seats in each row.")
                    .range("seatsPerRow", form.seatsPerRow(), 1, MAX_SEATS_PER_ROW, "Seats per row must be between 1 and " + MAX_SEATS_PER_ROW + ".");
        } else {
            v.required("capacity", form.capacity(), "Enter the capacity.")
                    .range("capacity", form.capacity(), 1, 100_000, "Capacity must be between 1 and 100,000.");
        }

        LocalDateTime startsAt = parseDateTime(v, "startsAt", form.startsAt());
        LocalDateTime endsAt = parseDateTime(v, "endsAt", form.endsAt());
        if (startsAt != null) {
            boolean dateChanged = existing == null || !startsAt.equals(existing.getStartsAt());
            if (dateChanged) {
                v.check(startsAt.isAfter(now), "startsAt", "The start time must be in the future.");
            }
            v.check(startsAt.isBefore(now.plusYears(5)), "startsAt", "The start time is too far in the future.");
        }
        if (startsAt != null && endsAt != null) {
            v.check(endsAt.isAfter(startsAt), "endsAt", "The end time must be after the start time.");
            v.check(!endsAt.isAfter(startsAt.plusDays(30)), "endsAt", "An event can last at most 30 days.");
        }
        if (imageUrl != null) {
            v.check(isValidImageUrl(imageUrl), "imageUrl", "Use a full http(s) image URL or a /images/... path (max 500 characters).");
        }
        v.throwIfInvalid();

        Event e = new Event();
        e.setTitle(title);
        e.setDescription(description);
        e.setCategoryId(form.categoryId());
        e.setVenueName(venueName);
        e.setCity(city);
        e.setVenueAddress(address);
        e.setStartsAt(startsAt);
        e.setEndsAt(endsAt);
        e.setTicketPrice(Money.normalize(form.ticketPrice()));
        e.setSeatingType(seating);
        if (seating == SeatingType.RESERVED) {
            e.setSeatRows(form.seatRows());
            e.setSeatsPerRow(form.seatsPerRow());
            e.setCapacity(form.seatRows() * form.seatsPerRow());     // capacity follows the layout
        } else {
            e.setCapacity(form.capacity());
        }
        e.setImageUrl(imageUrl);
        e.setFeatured(Boolean.TRUE.equals(form.featured()));   // null-safe Boolean check
        return e;
    }

    private static LocalDateTime parseDateTime(Validator v, String field, String value) {
        if (TextUtils.isBlank(value)) {
            v.check(false, field, "Enter a date and time.");
            return null;
        }
        try {
            return LocalDateTime.parse(value.strip()).withSecond(0).withNano(0);
        } catch (DateTimeParseException e) {
            v.check(false, field, "Enter a valid date and time (YYYY-MM-DDTHH:MM).");
            return null;
        }
    }

    static boolean isValidImageUrl(String url) {
        if (url.length() > 500) {
            return false;
        }
        if (url.startsWith("/images/") && !url.contains("..")) {
            return true;
        }
        try {
            URI uri = URI.create(url);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

}
