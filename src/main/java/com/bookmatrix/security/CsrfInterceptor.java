package com.bookmatrix.security;

import com.bookmatrix.exception.UnauthorizedActionException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * CSRF protection (synchroniser-token pattern).
 * A malicious site can make the browser SEND our session cookie, but it cannot READ our
 * token or set a custom header. So every POST/PUT/PATCH/DELETE must carry the
 * X-CSRF-Token header that matches the token stored in the session.
 */
@Component
public class CsrfInterceptor implements HandlerInterceptor {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return true;
        }
        HttpSession session = request.getSession(false);
        String expected = session == null ? null : (String) session.getAttribute(SessionKeys.CSRF_TOKEN);
        String provided = request.getHeader(SessionKeys.CSRF_HEADER);
        if (!CsrfTokens.matches(expected, provided)) {
            throw new CsrfRejectedException();
        }
        return true;
    }

    /** 403 with its own code so the frontend knows to fetch a fresh token and retry. */
    public static class CsrfRejectedException extends UnauthorizedActionException {
        public CsrfRejectedException() {
            super("Your security token is missing or expired. Please refresh the page and try again.");
        }

        @Override
        public String code() {
            return "CSRF_INVALID";
        }
    }
}
