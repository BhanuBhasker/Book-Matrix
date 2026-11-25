package com.bookmatrix.security;

import com.bookmatrix.dao.UserDao;
import com.bookmatrix.db.Database;
import com.bookmatrix.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Protected HTML pages: not logged in -> redirect to the login page;
 * logged in as a normal user on an /admin page -> redirect home.
 * (The data behind these pages is protected separately by AuthInterceptor.)
 */
@Component
public class PageAccessInterceptor implements HandlerInterceptor {

    private final Database db;
    private final UserDao userDao;

    public PageAccessInterceptor(Database db, UserDao userDao) {
        this.db = db;
        this.userDao = userDao;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        Optional<User> user = Optional.empty();
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SessionKeys.USER_ID) instanceof Long id) {
            try {
                user = db.query(conn -> userDao.findById(conn, id)).filter(User::isActive);
            } catch (RuntimeException e) {
                user = Optional.empty();    // database down: treat as logged out, login page explains
            }
        }
        String uri = request.getRequestURI();
        if (user.isEmpty()) {
            String next = uri + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
            response.sendRedirect("/login.html?next=" + URLEncoder.encode(next, StandardCharsets.UTF_8));
            return false;
        }
        if (uri.startsWith("/admin") && !user.get().isAdmin()) {
            response.sendRedirect("/?denied=admin");
            return false;
        }
        return true;
    }
}
