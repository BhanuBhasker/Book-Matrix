package com.bookmatrix.dao;

import com.bookmatrix.db.JdbcDao;
import com.bookmatrix.db.SqlWhere;
import com.bookmatrix.dto.BookingFilter;
import com.bookmatrix.model.Booking;
import com.bookmatrix.model.BookingStatus;
import com.bookmatrix.model.EventStatus;
import com.bookmatrix.util.PageRequest;
import com.bookmatrix.util.TextUtils;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** All SQL for bookings. */
@Repository
public class BookingDao extends JdbcDao {

    private static final String SELECT =
            "SELECT b.id, b.booking_ref, b.user_id, b.event_id, b.quantity, b.seat_labels, b.unit_price, b.total_amount, b.status, "
          + "b.request_key, b.booked_at, b.hold_expires_at, b.paid_at, b.cancelled_at, b.cancelled_by, b.cancellation_reason, "
          + "e.title AS event_title, e.starts_at AS event_starts_at, e.ends_at AS event_ends_at, e.status AS event_status, "
          + "v.name AS venue_name, v.city, u.full_name AS user_name, u.email AS user_email, cu.full_name AS cancelled_by_name "
          + "FROM bookings b JOIN events e ON e.id = b.event_id JOIN venues v ON v.id = e.venue_id "
          + "JOIN users u ON u.id = b.user_id LEFT JOIN users cu ON cu.id = b.cancelled_by";

    public long insert(Connection conn, Booking b) throws SQLException {
        return insert(conn,
                "INSERT INTO bookings (booking_ref, user_id, event_id, quantity, seat_labels, unit_price, total_amount, status, "
              + "request_key, booked_at, hold_expires_at, paid_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                b.getReference(), b.getUserId(), b.getEventId(), b.getQuantity(), b.getSeatLabels(), b.getUnitPrice(),
                b.getTotalAmount(), b.getStatus(), b.getRequestKey(), b.getBookedAt(), b.getHoldExpiresAt(), b.getPaidAt());
    }

    /** PENDING_PAYMENT -> CONFIRMED. The status guard means a booking can only be paid once. */
    public int markPaid(Connection conn, long bookingId, LocalDateTime now) throws SQLException {
        return update(conn, "UPDATE bookings SET status = 'CONFIRMED', paid_at = ? WHERE id = ? AND status = 'PENDING_PAYMENT'",
                now, bookingId);
    }

    /** PENDING_PAYMENT -> EXPIRED when the payment window has passed. */
    public int markExpired(Connection conn, long bookingId) throws SQLException {
        return update(conn, "UPDATE bookings SET status = 'EXPIRED' WHERE id = ? AND status = 'PENDING_PAYMENT'", bookingId);
    }

    /** Pending bookings whose hold has run out (oldest first). */
    public List<String> findExpiredHolds(Connection conn, LocalDateTime now, int limit) throws SQLException {
        return queryList(conn, "SELECT booking_ref FROM bookings WHERE status = 'PENDING_PAYMENT' AND hold_expires_at <= ? "
                + "ORDER BY hold_expires_at LIMIT ?", rs -> rs.getString(1), now, limit);
    }

    public Optional<Booking> findByReference(Connection conn, String reference) throws SQLException {
        return queryOne(conn, SELECT + " WHERE b.booking_ref = ?", BookingDao::map, reference);
    }

    /** Locks the booking row so two cancel clicks cannot both restore inventory. */
    public Optional<Booking> findByReferenceForUpdate(Connection conn, String reference) throws SQLException {
        return queryOne(conn, SELECT + " WHERE b.booking_ref = ? FOR UPDATE OF b", BookingDao::map, reference);
    }

    public Optional<Booking> findByRequestKey(Connection conn, long userId, String requestKey) throws SQLException {
        return queryOne(conn, SELECT + " WHERE b.user_id = ? AND b.request_key = ?", BookingDao::map, userId, requestKey);
    }

    /**
     * Flips CONFIRMED -> CANCELLED. The "AND status = 'CONFIRMED'" guard means the second of two
     * simultaneous cancellations changes 0 rows, so inventory is restored exactly once.
     */
    public int markCancelled(Connection conn, long bookingId, long cancelledBy, String reason, LocalDateTime now)
            throws SQLException {
        return update(conn, "UPDATE bookings SET status = 'CANCELLED', cancelled_at = ?, cancelled_by = ?, "
                + "cancellation_reason = ? WHERE id = ? AND status IN ('CONFIRMED', 'PENDING_PAYMENT')", now, cancelledBy, reason, bookingId);
    }

    /** Used when an admin cancels a whole event: every confirmed booking is cancelled in the same transaction. */
    public int cancelAllForEvent(Connection conn, long eventId, long cancelledBy, String reason, LocalDateTime now)
            throws SQLException {
        return update(conn, "UPDATE bookings SET status = 'CANCELLED', cancelled_at = ?, cancelled_by = ?, "
                + "cancellation_reason = ? WHERE event_id = ? AND status IN ('CONFIRMED', 'PENDING_PAYMENT')", now, cancelledBy, reason, eventId);
    }

    public List<Booking> findActiveUpcomingForUser(Connection conn, long userId, LocalDateTime now) throws SQLException {
        return queryList(conn, SELECT + " WHERE b.user_id = ? AND b.status IN ('CONFIRMED', 'PENDING_PAYMENT') AND e.starts_at > ?",
                BookingDao::map, userId, now);
    }

    public long countForUser(Connection conn, long userId) throws SQLException {
        return queryLong(conn, "SELECT COUNT(*) FROM bookings WHERE user_id = ?", userId);
    }

    public long countForEvent(Connection conn, long eventId) throws SQLException {
        return queryLong(conn, "SELECT COUNT(*) FROM bookings WHERE event_id = ?", eventId);
    }

    public long confirmedTicketsForEvent(Connection conn, long eventId) throws SQLException {
        return queryLong(conn, "SELECT COALESCE(SUM(quantity),0) FROM bookings WHERE event_id = ? AND status = 'CONFIRMED'",
                eventId);
    }

    /** Search used by both "My bookings" (userId set) and admin screens (userId null). */
    public List<Booking> search(Connection conn, Long userId, Long eventId, String text, BookingFilter filter,
                                PageRequest page, LocalDateTime now) throws SQLException {
        SqlWhere where = buildWhere(userId, eventId, text, filter, now);
        return queryList(conn, SELECT + where.toSql() + " ORDER BY b.booked_at DESC, b.id DESC LIMIT ? OFFSET ?",
                BookingDao::map, where.paramsWith(page.size(), page.offset()));
    }

    public long count(Connection conn, Long userId, Long eventId, String text, BookingFilter filter, LocalDateTime now)
            throws SQLException {
        SqlWhere where = buildWhere(userId, eventId, text, filter, now);
        return queryLong(conn, "SELECT COUNT(*) FROM bookings b JOIN events e ON e.id = b.event_id "
                + "JOIN venues v ON v.id = e.venue_id JOIN users u ON u.id = b.user_id" + where.toSql(), where.params());
    }

    /** Every booking (for CSV export). */
    public List<Booking> findAll(Connection conn) throws SQLException {
        return queryList(conn, SELECT + " ORDER BY b.booked_at, b.id", BookingDao::map);
    }

    private SqlWhere buildWhere(Long userId, Long eventId, String text, BookingFilter filter, LocalDateTime now) {
        SqlWhere where = new SqlWhere();
        if (userId != null) {
            where.add("b.user_id = ?", userId);
        }
        if (eventId != null) {
            where.add("b.event_id = ?", eventId);
        }
        if (!TextUtils.isBlank(text)) {
            String like = "%" + TextUtils.escapeLike(text) + "%";
            if (userId != null) {
                where.add("(b.booking_ref LIKE ? OR e.title LIKE ? OR v.name LIKE ?)", like, like, like);
            } else {
                where.add("(b.booking_ref LIKE ? OR e.title LIKE ? OR u.full_name LIKE ? OR u.email LIKE ?)",
                        like, like, like, like);
            }
        }
        filter.apply(where, now);
        return where;
    }

    static Booking map(ResultSet rs) throws SQLException {
        Booking b = new Booking();
        b.setId(rs.getLong("id"));
        b.setReference(rs.getString("booking_ref"));
        b.setUserId(rs.getLong("user_id"));
        b.setEventId(rs.getLong("event_id"));
        b.setQuantity(rs.getInt("quantity"));
        b.setSeatLabels(rs.getString("seat_labels"));
        b.setHoldExpiresAt(dateTime(rs, "hold_expires_at"));
        b.setPaidAt(dateTime(rs, "paid_at"));
        b.setUnitPrice(rs.getBigDecimal("unit_price"));
        b.setTotalAmount(rs.getBigDecimal("total_amount"));
        b.setStatus(BookingStatus.valueOf(rs.getString("status")));
        b.setRequestKey(rs.getString("request_key"));
        b.setBookedAt(dateTime(rs, "booked_at"));
        b.setCreatedAt(b.getBookedAt());
        b.setCancelledAt(dateTime(rs, "cancelled_at"));
        b.setCancelledBy(nullableLong(rs, "cancelled_by"));
        b.setCancelledByName(rs.getString("cancelled_by_name"));
        b.setCancellationReason(rs.getString("cancellation_reason"));
        b.setEventTitle(rs.getString("event_title"));
        b.setEventStartsAt(dateTime(rs, "event_starts_at"));
        b.setEventEndsAt(dateTime(rs, "event_ends_at"));
        b.setEventStatus(EventStatus.valueOf(rs.getString("event_status")));
        b.setVenueName(rs.getString("venue_name"));
        b.setCity(rs.getString("city"));
        b.setUserName(rs.getString("user_name"));
        b.setUserEmail(rs.getString("user_email"));
        return b;
    }
}
