package com.bookmatrix.security;

import com.bookmatrix.config.BookMatrixProperties;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt password hashing. BCrypt adds a random salt and is deliberately slow,
 * so stolen hashes are very expensive to crack. Plain passwords are never stored.
 */
@Component
public class PasswordHasher {

    private final BCryptPasswordEncoder encoder;
    private final String dummyHash;

    public PasswordHasher(BookMatrixProperties properties) {
        this.encoder = new BCryptPasswordEncoder(properties.bcryptStrength());
        this.dummyHash = encoder.encode("timing-equaliser-1");
    }

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String hash) {
        return rawPassword != null && hash != null && encoder.matches(rawPassword, hash);
    }

    /** Burns the same time as a real check, so attackers cannot tell which emails exist by timing. */
    public void simulateCheck(String rawPassword) {
        encoder.matches(rawPassword == null ? "" : rawPassword, dummyHash);
    }
}
