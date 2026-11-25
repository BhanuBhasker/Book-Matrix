package com.bookmatrix.util;

import java.security.SecureRandom;

/**
 * Creates booking references like "BM7K2Q9XWD".
 * Uses SecureRandom (unpredictable, so nobody can guess other people's references)
 * and StringBuilder (efficient when building a String piece by piece).
 * Ambiguous characters (0/O, 1/I/L) are left out so references are easy to read aloud.
 */
public final class BookingReferenceGenerator {

    public static final String PREFIX = "BM";
    public static final int LENGTH = 10;
    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private BookingReferenceGenerator() {
        // static utility class: no objects needed
    }

    public static String next() {
        StringBuilder sb = new StringBuilder(LENGTH);
        sb.append(PREFIX);
        while (sb.length() < LENGTH) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }

    /** Validates user-supplied references before they ever reach SQL. */
    public static boolean isWellFormed(String reference) {
        if (reference == null || reference.length() != LENGTH || !reference.startsWith(PREFIX)) {
            return false;
        }
        for (int i = PREFIX.length(); i < reference.length(); i++) {
            if (new String(ALPHABET).indexOf(reference.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }
}
