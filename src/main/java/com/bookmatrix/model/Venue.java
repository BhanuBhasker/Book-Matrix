package com.bookmatrix.model;

/** A place where events happen. One venue has many events. */
public record Venue(int id, String name, String city, String address) {
}
