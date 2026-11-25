package com.bookmatrix.util;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Single source of "now". Every time comparison (has the event started? can this booking
 * be cancelled?) uses this, and the value is passed into SQL as a parameter, so Java
 * and MySQL always agree on the current time.
 */
@Component
public class TimeProvider {

    public LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
