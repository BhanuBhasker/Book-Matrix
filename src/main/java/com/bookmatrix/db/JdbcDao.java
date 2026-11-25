package com.bookmatrix.db;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Base class with the repetitive JDBC plumbing, shared by every DAO through inheritance.
 *
 * Every method uses a PreparedStatement with "?" placeholders. Values are BOUND as
 * parameters, never glued into the SQL text, so input like  ' OR 1=1 --  is treated
 * as plain data. This is what stops SQL injection.
 */
public abstract class JdbcDao {

    /** SELECT returning many rows. executeQuery() + a while(rs.next()) cursor loop. */
    protected <T> List<T> queryList(Connection conn, String sql, RowMapper<T> mapper, Object... params)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> results = new ArrayList<>();
                while (rs.next()) {                 // move the cursor row by row
                    results.add(mapper.map(rs));
                }
                return results;
            }
        }
    }

    protected <T> Optional<T> queryOne(Connection conn, String sql, RowMapper<T> mapper, Object... params)
            throws SQLException {
        List<T> rows = queryList(conn, sql, mapper, params);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    protected long queryLong(Connection conn, String sql, Object... params) throws SQLException {
        return queryOne(conn, sql, rs -> rs.getLong(1), params).orElse(0L);
    }

    /** INSERT/UPDATE/DELETE. executeUpdate() returns how many rows changed. */
    protected int update(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    /** INSERT that returns the AUTO_INCREMENT id MySQL generated. */
    protected long insert(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, params);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Insert did not return a generated key");
                }
                return keys.getLong(1);
            }
        }
    }

    /** Parameter binding: index 1 = first "?". Typed setters are used for each Java type. */
    protected static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            int index = i + 1;
            Object value = params[i];
            if (value == null) {
                ps.setNull(index, Types.NULL);
            } else if (value instanceof String s) {
                ps.setString(index, s);
            } else if (value instanceof Integer n) {
                ps.setInt(index, n);
            } else if (value instanceof Long n) {
                ps.setLong(index, n);
            } else if (value instanceof BigDecimal d) {
                ps.setBigDecimal(index, d);
            } else if (value instanceof Boolean b) {
                ps.setBoolean(index, b);
            } else if (value instanceof Enum<?> e) {
                ps.setString(index, e.name());
            } else if (value instanceof LocalDateTime || value instanceof LocalDate) {
                ps.setObject(index, value);
            } else {
                throw new SQLException("Unsupported parameter type: " + value.getClass().getName());
            }
        }
    }

    /** Typed getter helpers that keep SQL NULL as Java null. */
    protected static LocalDateTime dateTime(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDateTime.class);
    }

    protected static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;   // autoboxing long -> Long
    }
}
