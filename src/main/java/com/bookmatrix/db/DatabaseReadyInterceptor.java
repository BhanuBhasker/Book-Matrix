package com.bookmatrix.db;

import com.bookmatrix.exception.DatabaseUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.sql.SQLException;

/** Before any API call: make sure the schema exists. Otherwise answer 503 with a clear message. */
@Component
public class DatabaseReadyInterceptor implements HandlerInterceptor {

    private final DatabaseInitializer initializer;

    public DatabaseReadyInterceptor(DatabaseInitializer initializer) {
        this.initializer = initializer;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getRequestURI().equals("/api/health") || initializer.ensureReady()) {
            return true;
        }
        throw new DatabaseUnavailableException(new SQLException("Database not initialised", "08001"));
    }
}
