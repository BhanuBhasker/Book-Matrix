package com.bookmatrix.validation;

import java.util.Optional;

/** Password strength rules, in one place so registration, password change and admin bootstrap agree. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 72;   // BCrypt only uses the first 72 bytes

    private PasswordPolicy() {
    }

    /** Returns the first problem with the password, or empty if it is acceptable. */
    public static Optional<String> problem(String password, String email) {
        if (password == null || password.isEmpty()) {
            return Optional.of("Enter a password.");
        }
        if (password.length() < MIN_LENGTH) {
            return Optional.of("Use at least " + MIN_LENGTH + " characters.");
        }
        if (password.length() > MAX_LENGTH) {
            return Optional.of("Use at most " + MAX_LENGTH + " characters.");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : password.toCharArray()) {
            hasLetter |= Character.isLetter(c);
            hasDigit |= Character.isDigit(c);
        }
        if (!hasLetter || !hasDigit) {
            return Optional.of("Use at least one letter and one number.");
        }
        if (password.isBlank() || password.strip().length() != password.length()) {
            return Optional.of("Password cannot start or end with spaces.");
        }
        if (email != null && password.equalsIgnoreCase(email)) {
            return Optional.of("Password cannot be the same as your email.");
        }
        return Optional.empty();
    }
}
