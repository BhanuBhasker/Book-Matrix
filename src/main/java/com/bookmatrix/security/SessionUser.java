package com.bookmatrix.security;

import com.bookmatrix.model.Role;
import com.bookmatrix.model.User;

/** The logged-in user for the current request (freshly loaded from the database each time). */
public record SessionUser(long id, String fullName, String email, Role role) {

    public static SessionUser from(User user) {
        return new SessionUser(user.getId(), user.getFullName(), user.getEmail(), user.getRole());
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
