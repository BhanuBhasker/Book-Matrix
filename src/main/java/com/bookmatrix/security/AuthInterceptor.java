package com.bookmatrix.security;

import com.bookmatrix.dao.UserDao;
import com.bookmatrix.db.Database;
import com.bookmatrix.exception.AuthenticationRequiredException;
import com.bookmatrix.exception.UnauthorizedActionException;
import com.bookmatrix.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * Authentication + role-based authorisation for the REST API. Rules (checked on the SERVER):
 *   /api/admin/**                 -> must be logged in AND have role ADMIN
 *   /api/me/**, /api/bookings/**  -> must be logged in
 *   everything else               -> public
 * The user is re-read from MySQL on every request, so a deactivated account or a changed
 * role takes effect immediately, even for sessions that are already open.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final Database db;
    private final UserDao userDao;

    public AuthInterceptor(Database db, UserDao userDao) {
        this.db = db;
        this.userDao = userDao;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Optional<SessionUser> current = resolveUser(request);
        current.ifPresent(u -> request.setAttribute(SessionKeys.CURRENT_USER, u));

        String path = request.getRequestURI();
        boolean adminArea = path.startsWith("/api/admin/");
        boolean memberArea = path.startsWith("/api/me") || path.startsWith("/api/bookings");

        if ((adminArea || memberArea) && current.isEmpty()) {
            boolean expired = request.getRequestedSessionId() != null && !request.isRequestedSessionIdValid();
            throw expired
                    ? new AuthenticationRequiredException("SESSION_EXPIRED", "Your session has expired. Please log in again.")
                    : new AuthenticationRequiredException("Please log in to continue.");
        }
        if (adminArea && !current.get().isAdmin()) {
            throw new UnauthorizedActionException("Administrator access is required.");
        }
        return true;
    }

    /** Looks up the session's user id in MySQL. Deactivated or deleted users are logged out. */
    Optional<SessionUser> resolveUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(SessionKeys.USER_ID) instanceof Long userId)) {
            return Optional.empty();
        }
        Optional<User> user = db.query(conn -> userDao.findById(conn, userId));
        if (user.isEmpty() || !user.get().isActive()) {
            session.removeAttribute(SessionKeys.USER_ID);
            return Optional.empty();
        }
        return user.map(SessionUser::from);
    }
}
