package com.bookmatrix.controller;

import com.bookmatrix.db.DatabaseInitializer;
import com.bookmatrix.util.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** GET /api/health -> 200 when the database is usable, 503 otherwise. Reveals no configuration. */
@RestController
public class HealthController {

    private final DatabaseInitializer initializer;

    public HealthController(DatabaseInitializer initializer) {
        this.initializer = initializer;
    }

    @GetMapping("/api/health")
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {
        boolean ok = initializer.ensureReady();
        Map<String, String> body = Map.of("application", "UP", "database", ok ? "UP" : "DOWN");
        if (ok) {
            return ResponseEntity.ok(ApiResponse.ok(body));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiResponse<>(false, body, null,
                new ApiResponse.ErrorBody("DATABASE_UNAVAILABLE", "The database is currently unavailable.", Map.of())));
    }
}
