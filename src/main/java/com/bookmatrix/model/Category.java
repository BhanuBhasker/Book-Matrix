package com.bookmatrix.model;

/** Event category (Music, Comedy, ...). A record is an immutable data class. */
public record Category(int id, String name, String slug, String description) {
}
