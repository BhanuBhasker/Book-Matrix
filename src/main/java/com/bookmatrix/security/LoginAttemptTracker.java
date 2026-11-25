package com.bookmatrix.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing: after 5 wrong passwords for one email, that email is locked
 * for 5 minutes. ConcurrentHashMap is a thread-safe map — many requests use it at once.
 */
@Component
public class LoginAttemptTracker {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_TIME = Duration.ofMinutes(5);

    private record Attempts(int failures, Instant lockedUntil) { }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public boolean isLocked(String email) {
        Attempts a = attempts.get(email);
        return a != null && a.lockedUntil() != null && Instant.now().isBefore(a.lockedUntil());
    }

    public void recordFailure(String email) {
        attempts.compute(email, (key, old) -> {
            int failures = (old == null || old.lockedUntil() != null) ? 1 : old.failures() + 1;
            Instant lock = failures >= MAX_FAILURES ? Instant.now().plus(LOCK_TIME) : null;
            return new Attempts(failures, lock);
        });
    }

    public void recordSuccess(String email) {
        attempts.remove(email);
    }
}
