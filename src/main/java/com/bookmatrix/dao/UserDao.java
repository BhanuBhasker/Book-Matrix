package com.bookmatrix.dao;

import com.bookmatrix.db.Dao;
import com.bookmatrix.db.JdbcDao;
import com.bookmatrix.db.SqlWhere;
import com.bookmatrix.model.AccountStatus;
import com.bookmatrix.model.Role;
import com.bookmatrix.model.User;
import com.bookmatrix.model.UserWithStats;
import com.bookmatrix.util.PageRequest;
import com.bookmatrix.util.TextUtils;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** All SQL for the users table. */
@Repository
public class UserDao extends JdbcDao implements Dao<User, Long> {

    private static final String COLUMNS =
            "u.id, u.full_name, u.email, u.phone, u.password_hash, u.role, u.status, "
          + "u.created_at, u.updated_at, u.last_login_at, u.deactivated_at";

    @Override
    public Optional<User> findById(Connection conn, Long id) throws SQLException {
        return queryOne(conn, "SELECT " + COLUMNS + " FROM users u WHERE u.id = ?", UserDao::mapUser, id);
    }

    public Optional<User> findByEmail(Connection conn, String email) throws SQLException {
        return queryOne(conn, "SELECT " + COLUMNS + " FROM users u WHERE u.email = ?", UserDao::mapUser, email);
    }

    public boolean emailExists(Connection conn, String email) throws SQLException {
        return queryLong(conn, "SELECT COUNT(*) FROM users WHERE email = ?", email) > 0;
    }

    public long insert(Connection conn, User user, LocalDateTime now) throws SQLException {
        return insert(conn,
                "INSERT INTO users (full_name, email, phone, password_hash, role, status, created_at, updated_at) "
              + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                user.getFullName(), user.getEmail(), user.getPhone(), user.getPasswordHash(),
                user.getRole(), user.getStatus(), now, now);
    }

    public int updateProfile(Connection conn, long id, String fullName, String phone) throws SQLException {
        return update(conn, "UPDATE users SET full_name = ?, phone = ? WHERE id = ?", fullName, phone, id);
    }

    public int updatePassword(Connection conn, long id, String passwordHash) throws SQLException {
        return update(conn, "UPDATE users SET password_hash = ? WHERE id = ?", passwordHash, id);
    }

    public int updateLastLogin(Connection conn, long id, LocalDateTime when) throws SQLException {
        return update(conn, "UPDATE users SET last_login_at = ? WHERE id = ?", when, id);
    }

    public int updateRole(Connection conn, long id, Role role) throws SQLException {
        return update(conn, "UPDATE users SET role = ? WHERE id = ?", role, id);
    }

    public int updateStatus(Connection conn, long id, AccountStatus status, LocalDateTime now) throws SQLException {
        LocalDateTime deactivatedAt = status == AccountStatus.DEACTIVATED ? now : null;
        return update(conn, "UPDATE users SET status = ?, deactivated_at = ? WHERE id = ?", status, deactivatedAt, id);
    }

    public int deleteById(Connection conn, long id) throws SQLException {
        return update(conn, "DELETE FROM users WHERE id = ?", id);
    }

    public boolean hasAnyAdmin(Connection conn) throws SQLException {
        return queryLong(conn, "SELECT COUNT(*) FROM users WHERE role = 'ADMIN'") > 0;
    }

    /**
     * Counts active admins and LOCKS those rows (FOR UPDATE) until the transaction ends.
     * Two admins demoting each other at the same moment therefore cannot both succeed
     * and leave the platform with zero admins.
     */
    public int countActiveAdminsForUpdate(Connection conn) throws SQLException {
        return queryList(conn, "SELECT id FROM users WHERE role = 'ADMIN' AND status = 'ACTIVE' FOR UPDATE",
                rs -> rs.getLong(1)).size();
    }

    public List<UserWithStats> search(Connection conn, String text, Role role, AccountStatus status,
                                      PageRequest page) throws SQLException {
        SqlWhere where = buildWhere(text, role, status);
        String sql = "SELECT " + COLUMNS + ", "
                + " (SELECT COUNT(*) FROM bookings b WHERE b.user_id = u.id) AS booking_count, "
                + " (SELECT COALESCE(SUM(b.quantity),0) FROM bookings b WHERE b.user_id = u.id AND b.status='CONFIRMED') AS tickets_held, "
                + " (SELECT COALESCE(SUM(b.total_amount),0) FROM bookings b WHERE b.user_id = u.id AND b.status='CONFIRMED') AS total_spent "
                + " FROM users u" + where.toSql() + " ORDER BY u.created_at DESC, u.id DESC LIMIT ? OFFSET ?";
        return queryList(conn, sql, rs -> new UserWithStats(mapUser(rs), rs.getLong("booking_count"),
                rs.getLong("tickets_held"), rs.getBigDecimal("total_spent")), where.paramsWith(page.size(), page.offset()));
    }

    public long count(Connection conn, String text, Role role, AccountStatus status) throws SQLException {
        SqlWhere where = buildWhere(text, role, status);
        return queryLong(conn, "SELECT COUNT(*) FROM users u" + where.toSql(), where.params());
    }

    private SqlWhere buildWhere(String text, Role role, AccountStatus status) {
        SqlWhere where = new SqlWhere();
        if (!TextUtils.isBlank(text)) {
            String like = "%" + TextUtils.escapeLike(text) + "%";
            where.add("(u.full_name LIKE ? OR u.email LIKE ?)", like, like);
        }
        if (role != null) {
            where.add("u.role = ?", role);
        }
        if (status != null) {
            where.add("u.status = ?", status);
        }
        return where;
    }

    /** ResultSet -> User object, using typed getters (getLong, getString...). */
    static User mapUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setFullName(rs.getString("full_name"));
        user.setEmail(rs.getString("email"));
        user.setPhone(rs.getString("phone"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setRole(Role.valueOf(rs.getString("role")));
        user.setStatus(AccountStatus.valueOf(rs.getString("status")));
        user.setCreatedAt(dateTime(rs, "created_at"));
        user.setUpdatedAt(dateTime(rs, "updated_at"));
        user.setLastLoginAt(dateTime(rs, "last_login_at"));
        user.setDeactivatedAt(dateTime(rs, "deactivated_at"));
        return user;
    }
}
