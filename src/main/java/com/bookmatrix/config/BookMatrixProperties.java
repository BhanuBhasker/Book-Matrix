package com.bookmatrix.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the "bookmatrix.*" settings in application.properties. */
@ConfigurationProperties(prefix = "bookmatrix")
public record BookMatrixProperties(int maxTicketsPerBooking, int cancellationCutoffHours, int bcryptStrength,
                                   String backupDir, int paymentHoldMinutes, Admin admin) {

    public record Admin(String name, String email, String password) {
    }
}
