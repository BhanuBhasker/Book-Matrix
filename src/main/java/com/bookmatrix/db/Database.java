package com.bookmatrix.db;

import com.bookmatrix.exception.BookMatrixException;
import com.bookmatrix.exception.ConflictException;
import com.bookmatrix.exception.DatabaseException;
import com.bookmatrix.exception.DatabaseUnavailableException;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTransientConnectionException;

/**
 * The one place where JDBC connections are opened, committed, rolled back and closed.
 *
 * - query(...)          : one auto-commit operation (reads, single updates)
 * - inTransaction(...)  : several statements that must succeed or fail TOGETHER
 *
 * Connections come from the HikariCP pool (a DataSource). try-with-resources guarantees
 * conn.close() is always called, which hands the connection back to the pool — no leaks.
 */
@Component
public class Database {

    private final DataSource dataSource;

    public Database(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Runs work on a connection in auto-commit mode. */
    public <R> R query(SqlFunction<Connection, R> work) {
        try (Connection conn = dataSource.getConnection()) {
            return work.apply(conn);
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    /**
     * Runs work inside a database transaction.
     * commit() only if everything succeeded; rollback() on ANY exception, so a half-finished
     * booking (e.g. inventory reduced but no booking row) can never be saved.
     */
    public <R> R inTransaction(SqlFunction<Connection, R> work) {
        try (Connection conn = dataSource.getConnection()) {
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                R result = work.apply(conn);
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException e) {     // multi-catch
                rollbackQuietly(conn, e);
                throw e;
            } finally {
                conn.setAutoCommit(previousAutoCommit);        // always restore before returning to the pool
            }
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    private static void rollbackQuietly(Connection conn, Exception original) {
        try {
            conn.rollback();
        } catch (SQLException rollbackError) {
            original.addSuppressed(rollbackError);
        }
    }

    /** Converts a checked SQLException into one of our unchecked exceptions. */
    public static BookMatrixException translate(SQLException e) {
        if (isConnectionProblem(e)) {
            return new DatabaseUnavailableException(e);
        }
        if (e instanceof SQLIntegrityConstraintViolationException || e.getErrorCode() == 3819) {
            // 1062 duplicate key, 1451/1452 foreign key, 3819 CHECK constraint
            return new ConflictException("CONSTRAINT_VIOLATION",
                    "The change was rejected because it conflicts with existing data.");
        }
        return new DatabaseException("A database error occurred.", e);
    }

    public static boolean isDuplicateKey(SQLException e) {
        return e.getErrorCode() == 1062;
    }

    private static boolean isConnectionProblem(SQLException e) {
        String state = e.getSQLState();
        return e instanceof SQLTransientConnectionException
                || e instanceof SQLNonTransientConnectionException
                || (state != null && state.startsWith("08"));
    }
}
