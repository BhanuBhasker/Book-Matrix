package com.bookmatrix.model;

/**
 * The status shown to people. It is calculated from the stored status, dates and
 * inventory, so it can never contradict the data (e.g. "sold out" with seats left).
 */
public enum EventDisplayStatus {
    UPCOMING("Upcoming"),
    SOLD_OUT("Sold out"),
    LIVE("Happening now"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    ARCHIVED("Archived");

    private final String label;

    EventDisplayStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
