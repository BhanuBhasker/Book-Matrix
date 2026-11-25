package com.bookmatrix.security;

/** Names of values stored in the HttpSession / request. Constants avoid typos. */
public final class SessionKeys {

    public static final String USER_ID = "bm.userId";
    public static final String CSRF_TOKEN = "bm.csrf";
    public static final String CURRENT_USER = "bm.currentUser";
    public static final String CSRF_HEADER = "X-CSRF-Token";

    private SessionKeys() {
    }
}
