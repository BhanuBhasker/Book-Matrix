package com.bookmatrix.model;

import java.time.LocalDateTime;

/**
 * Parent class of every database-backed domain object.
 *
 * OOP concepts shown here:
 *  - abstraction: this class is abstract, you can never create a plain BaseEntity
 *  - encapsulation: fields are private and only reachable through getters/setters
 *  - inheritance: User, Event and Booking all "extend BaseEntity"
 *  - polymorphism: describe() is abstract and every subclass overrides it
 */
public abstract class BaseEntity {

    private Long id;
    private LocalDateTime createdAt;

    protected BaseEntity() {
    }

    protected BaseEntity(Long id, LocalDateTime createdAt) {
        this.id = id;               // "this" separates the field from the parameter
        this.createdAt = createdAt;
    }

    /** A short human readable label, implemented differently by each subclass. */
    public abstract String describe();

    public boolean isNew() {
        return id == null;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "#" + id + " (" + describe() + ")";
    }
}
