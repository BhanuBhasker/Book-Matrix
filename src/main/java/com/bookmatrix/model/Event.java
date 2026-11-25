package com.bookmatrix.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * An event people can book tickets for.
 *
 * Implements Comparable so a list of events has a "natural order" (by start time),
 * which Collections.sort(list) and TreeSet use automatically.
 */
public class Event extends BaseEntity implements Comparable<Event> {

    private String title;
    private String description;
    private int categoryId;
    private String categoryName;
    private int venueId;
    private String venueName;
    private String city;
    private String venueAddress;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private BigDecimal ticketPrice;
    private int capacity;
    private int ticketsSold;
    private SeatingType seatingType = SeatingType.GENERAL;
    private Integer seatRows;        // only for RESERVED seating
    private Integer seatsPerRow;     // only for RESERVED seating
    private String imageUrl;
    private boolean featured;
    private EventStatus status = EventStatus.SCHEDULED;
    private LocalDateTime cancelledAt;
    private LocalDateTime archivedAt;
    private LocalDateTime updatedAt;

    @Override
    public String describe() {
        return title + " @ " + venueName + ", " + city;
    }

    /** Natural ordering: earliest event first, then by title. */
    @Override
    public int compareTo(Event other) {
        int byDate = this.startsAt.compareTo(other.startsAt);
        return byDate != 0 ? byDate : this.title.compareToIgnoreCase(other.title);
    }

    public int getAvailableTickets() {
        return capacity - ticketsSold;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    /** Works out what to show the user. Order of checks matters: cancelled beats everything. */
    public EventDisplayStatus getDisplayStatus(LocalDateTime now) {
        if (isArchived()) {
            return EventDisplayStatus.ARCHIVED;
        }
        if (status == EventStatus.CANCELLED) {
            return EventDisplayStatus.CANCELLED;
        }
        if (!now.isBefore(endsAt)) {
            return EventDisplayStatus.COMPLETED;
        }
        if (!now.isBefore(startsAt)) {
            return EventDisplayStatus.LIVE;
        }
        if (getAvailableTickets() <= 0) {
            return EventDisplayStatus.SOLD_OUT;
        }
        return EventDisplayStatus.UPCOMING;
    }

    /** Empty when tickets can be booked, otherwise the reason they cannot. */
    public Optional<String> getBookingBlockReason(LocalDateTime now) {
        return switch (getDisplayStatus(now)) {
            case UPCOMING -> Optional.empty();
            case SOLD_OUT -> Optional.of("This event is sold out.");
            case LIVE -> Optional.of("This event has already started.");
            case COMPLETED -> Optional.of("This event has already finished.");
            case CANCELLED -> Optional.of("This event has been cancelled.");
            case ARCHIVED -> Optional.of("This event is no longer available.");
        };
    }

    public boolean isBookable(LocalDateTime now) {
        return getBookingBlockReason(now).isEmpty();
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getCategoryId() { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public int getVenueId() { return venueId; }
    public void setVenueId(int venueId) { this.venueId = venueId; }
    public String getVenueName() { return venueName; }
    public void setVenueName(String venueName) { this.venueName = venueName; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getVenueAddress() { return venueAddress; }
    public void setVenueAddress(String venueAddress) { this.venueAddress = venueAddress; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(LocalDateTime startsAt) { this.startsAt = startsAt; }
    public LocalDateTime getEndsAt() { return endsAt; }
    public void setEndsAt(LocalDateTime endsAt) { this.endsAt = endsAt; }
    public BigDecimal getTicketPrice() { return ticketPrice; }
    public void setTicketPrice(BigDecimal ticketPrice) { this.ticketPrice = ticketPrice; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public int getTicketsSold() { return ticketsSold; }
    public void setTicketsSold(int ticketsSold) { this.ticketsSold = ticketsSold; }
    public boolean isReservedSeating() { return seatingType == SeatingType.RESERVED; }
    public SeatingType getSeatingType() { return seatingType; }
    public void setSeatingType(SeatingType seatingType) { this.seatingType = seatingType; }
    public Integer getSeatRows() { return seatRows; }
    public void setSeatRows(Integer seatRows) { this.seatRows = seatRows; }
    public Integer getSeatsPerRow() { return seatsPerRow; }
    public void setSeatsPerRow(Integer seatsPerRow) { this.seatsPerRow = seatsPerRow; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) { this.featured = featured; }
    public EventStatus getStatus() { return status; }
    public void setStatus(EventStatus status) { this.status = status; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public LocalDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(LocalDateTime archivedAt) { this.archivedAt = archivedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
