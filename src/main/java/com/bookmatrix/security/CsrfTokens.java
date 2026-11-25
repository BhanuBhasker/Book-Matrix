package com.bookmatrix.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Creates and checks the per-session CSRF token. */
public final class CsrfTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private CsrfTokens() {
    }

    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Returns the session's token, creating the session and token if needed. */
    public static String getOrCreate(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        Object token = session.getAttribute(SessionKeys.CSRF_TOKEN);
        if (token instanceof String s) {
            return s;
        }
        String fresh = newToken();
        session.setAttribute(SessionKeys.CSRF_TOKEN, fresh);
        return fresh;
    }

    public static String rotate(HttpSession session) {
        String fresh = newToken();
        session.setAttribute(SessionKeys.CSRF_TOKEN, fresh);
        return fresh;
    }

    /** Constant-time comparison, so the check itself leaks nothing through timing. */
    public static boolean matches(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
