package com.bookmatrix.db;

import com.bookmatrix.security.AdminBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates the schema (db/schema.sql), loads reference data and bootstraps the first admin.
 *
 * If MySQL is down at start-up the application still starts: it logs a clear message,
 * API calls answer "503 database unavailable", and initialisation is retried automatically
 * on the next request once MySQL is back (see DatabaseReadyInterceptor).
 */
@Component
public class DatabaseInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);
    private static final long RETRY_INTERVAL_MS = 5_000;

    private final DataSource dataSource;
    private final AdminBootstrap adminBootstrap;
    private volatile boolean ready = false;          // volatile: every thread sees the latest value
    private volatile long lastAttemptAt = 0;
    private volatile boolean initializing = false;

    public DatabaseInitializer(DataSource dataSource, AdminBootstrap adminBootstrap) {
        this.dataSource = dataSource;
        this.adminBootstrap = adminBootstrap;
    }

    @Override
    public void run(ApplicationArguments args) {
        tryInitialize();
    }

    public boolean isReady() {
        return ready;
    }

    /** Called before API requests. Cheap when already initialised. */
    public boolean ensureReady() {
        if (ready) {
            return true;
        }
        if (initializing) {
            return tryInitialize();       // synchronized: waits for the running start-up/migration to finish
        }
        if (System.currentTimeMillis() - lastAttemptAt < RETRY_INTERVAL_MS) {
            return false;
        }
        return tryInitialize();
    }

    public synchronized boolean tryInitialize() {
        if (ready) {
            return true;
        }
        lastAttemptAt = System.currentTimeMillis();
        initializing = true;
        try (Connection conn = dataSource.getConnection()) {
            runScript(conn, "db/schema.sql");            // V1 baseline (idempotent)
            runScript(conn, "db/reference-data.sql");
            applyMigrations(conn);                        // V2, V3 ... each exactly once
            adminBootstrap.bootstrap(conn);
            ready = true;
            log.info("Database ready: schema verified on {}", conn.getMetaData().getURL().replaceAll("\\?.*", ""));
        } catch (SQLException e) {
            log.error("""

                    ==================================================================
                     BookMatrix cannot use the MySQL database right now.
                     Reason : {} (SQLState {})
                     Check  : is MySQL running? Are DB_URL / DB_USERNAME / DB_PASSWORD
                              in .env correct? See README -> "Database setup".
                     The web server keeps running and will retry automatically.
                    ==================================================================""",
                    e.getMessage(), e.getSQLState());
        } catch (IOException e) {
            log.error("Could not read the bundled SQL scripts", e);
        } finally {
            initializing = false;
        }
        return ready;
    }

    /** Schema changes after the first release, applied in order. Add new ones to the end. */
    record Migration(int version, String description, String resource) { }

    static final List<Migration> MIGRATIONS = List.of(
            new Migration(2, "payments and seat selection", "db/migrations/V2__payments_and_seats.sql"));

    /**
     * Applies each migration that has not run yet and records it in schema_migrations.
     * (MySQL commits DDL statements immediately, so each migration file is written to be
     * applied in one go on a working database.)
     */
    static void applyMigrations(Connection conn) throws IOException, SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS schema_migrations (version INT PRIMARY KEY, "
                    + "description VARCHAR(200) NOT NULL, applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        }
        for (Migration m : MIGRATIONS) {
            int version = m.version();
            boolean applied;
            try (var ps = conn.prepareStatement("SELECT COUNT(*) FROM schema_migrations WHERE version = ?")) {
                ps.setInt(1, version);
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    applied = rs.getInt(1) > 0;
                }
            }
            if (!applied) {
                log.info("Applying database migration V{} ({})", version, m.description());
                runScript(conn, m.resource());
                try (var ps = conn.prepareStatement("INSERT INTO schema_migrations (version, description) VALUES (?, ?)")) {
                    ps.setInt(1, version);
                    ps.setString(2, m.description());
                    ps.executeUpdate();
                }
            }
        }
    }

    /**
     * Reads a .sql file line by line and executes each statement with a plain JDBC Statement.
     * Statement (not PreparedStatement) is fine here because the SQL is fixed text from our
     * own file and never contains user input.
     */
    static void runScript(Connection conn, String resource) throws IOException, SQLException {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        ClassPathResource file = new ClassPathResource(resource);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                    continue;
                }
                current.append(line).append('\n');
                if (trimmed.endsWith(";")) {
                    String sql = current.toString().strip();
                    statements.add(sql.substring(0, sql.length() - 1));
                    current.setLength(0);
                }
            }
        }
        try (Statement statement = conn.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }
}
