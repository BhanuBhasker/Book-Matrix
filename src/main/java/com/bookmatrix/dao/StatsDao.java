package com.bookmatrix.dao;

import com.bookmatrix.db.JdbcDao;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Aggregate queries for the admin dashboard and inventory screen. */
@Repository
public class StatsDao extends JdbcDao {

    /**
     * Totals that do not depend on any input. Fixed SQL with no parameters is one of the few
     * places where a plain java.sql.Statement is appropriate.
     */
    public Map<String, Long> fixedTotals(Connection conn) throws SQLException {
        String sql = "SELECT "
                + "(SELECT COUNT(*) FROM users) AS total_users, "
                + "(SELECT COUNT(*) FROM users WHERE status = 'ACTIVE') AS active_users, "
                + "(SELECT COUNT(*) FROM users WHERE role = 'ADMIN') AS admins, "
                + "(SELECT COUNT(*) FROM events WHERE archived_at IS NULL) AS total_events, "
                + "(SELECT COUNT(*) FROM events WHERE archived_at IS NOT NULL) AS archived_events, "
                + "(SELECT COUNT(*) FROM events WHERE status = 'CANCELLED') AS cancelled_events, "
                + "(SELECT COUNT(*) FROM bookings) AS total_bookings, "
                + "(SELECT COUNT(*) FROM bookings WHERE status = 'CONFIRMED') AS confirmed_bookings, "
                + "(SELECT COUNT(*) FROM bookings WHERE status = 'CANCELLED') AS cancelled_bookings, "
                + "(SELECT COUNT(*) FROM bookings WHERE status = 'PENDING_PAYMENT') AS pending_bookings, "
                + "(SELECT COUNT(*) FROM bookings WHERE status = 'EXPIRED') AS expired_bookings, "
                + "(SELECT COALESCE(SUM(quantity),0) FROM bookings WHERE status = 'PENDING_PAYMENT') AS held_tickets, "
                + "(SELECT COUNT(*) FROM payments WHERE kind = 'CHARGE' AND status = 'SUCCEEDED') AS successful_payments, "
                + "(SELECT COUNT(*) FROM payments WHERE kind = 'CHARGE' AND status = 'FAILED') AS failed_payments, "
                + "(SELECT COUNT(*) FROM payments WHERE kind = 'REFUND') AS refunds, "
                + "(SELECT COALESCE(SUM(quantity),0) FROM bookings WHERE status = 'CONFIRMED') AS tickets_sold";
        Map<String, Long> totals = new LinkedHashMap<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                int columns = rs.getMetaData().getColumnCount();
                for (int i = 1; i <= columns; i++) {
                    totals.put(rs.getMetaData().getColumnLabel(i), rs.getLong(i));
                }
            }
        }
        return totals;
    }

    /** Totals that depend on "now", so they use a PreparedStatement. */
    public Map<String, Long> timeBasedTotals(Connection conn, LocalDateTime now) throws SQLException {
        Map<String, Long> totals = new LinkedHashMap<>();
        totals.put("upcoming_events", queryLong(conn,
                "SELECT COUNT(*) FROM events WHERE archived_at IS NULL AND status = 'SCHEDULED' AND starts_at > ?", now));
        totals.put("live_events", queryLong(conn,
                "SELECT COUNT(*) FROM events WHERE archived_at IS NULL AND status = 'SCHEDULED' AND starts_at <= ? AND ends_at > ?", now, now));
        totals.put("completed_events", queryLong(conn,
                "SELECT COUNT(*) FROM events WHERE status = 'SCHEDULED' AND ends_at <= ?", now));
        totals.put("sold_out_events", queryLong(conn,
                "SELECT COUNT(*) FROM events WHERE archived_at IS NULL AND status = 'SCHEDULED' AND starts_at > ? AND tickets_sold >= capacity", now));
        totals.put("available_inventory", queryLong(conn,
                "SELECT COALESCE(SUM(capacity - tickets_sold),0) FROM events WHERE archived_at IS NULL AND status = 'SCHEDULED' AND starts_at > ?", now));
        totals.put("bookings_last_7_days", queryLong(conn,
                "SELECT COUNT(*) FROM bookings WHERE booked_at >= ?", now.minusDays(7)));
        return totals;
    }

    /** Revenue = money from CONFIRMED bookings. Cancelled bookings are fully refunded, so excluded. */
    public BigDecimal confirmedRevenue(Connection conn) throws SQLException {
        return queryOne(conn, "SELECT COALESCE(SUM(total_amount),0) FROM bookings WHERE status = 'CONFIRMED'",
                rs -> rs.getBigDecimal(1)).orElse(BigDecimal.ZERO);
    }

    /** Money given back: cancelled bookings that had been paid (cancelled unpaid holds cost nothing). */
    public BigDecimal refundedAmount(Connection conn) throws SQLException {
        return queryOne(conn, "SELECT COALESCE(SUM(total_amount),0) FROM bookings WHERE status = 'CANCELLED' AND paid_at IS NOT NULL",
                rs -> rs.getBigDecimal(1)).orElse(BigDecimal.ZERO);
    }

    /**
     * One row per event: the stored counter next to inventory recomputed from bookings
     * (confirmed + held) and, for reserved seating, from the seats table.
     */
    public record InventoryRow(long eventId, String title, String city, LocalDateTime startsAt, String status,
                               boolean archived, int capacity, int ticketsSold, long confirmedTickets, long heldTickets,
                               Long seatsTaken, BigDecimal revenue) {
        public int available() {
            return capacity - ticketsSold;
        }

        /** Consistent when tickets_sold = paid + held tickets, and (reserved seating) = seats taken. */
        public boolean consistent() {
            return ticketsSold == confirmedTickets + heldTickets && (seatsTaken == null || seatsTaken == ticketsSold);
        }
    }

    public List<InventoryRow> inventory(Connection conn, boolean includeArchived) throws SQLException {
        String sql = "SELECT e.id, e.title, v.city, e.starts_at, e.status, e.archived_at, e.capacity, e.tickets_sold, "
                + "COALESCE(SUM(CASE WHEN b.status = 'CONFIRMED' THEN b.quantity END),0) AS confirmed_tickets, "
                + "COALESCE(SUM(CASE WHEN b.status = 'PENDING_PAYMENT' THEN b.quantity END),0) AS held_tickets, "
                + "COALESCE(SUM(CASE WHEN b.status = 'CONFIRMED' THEN b.total_amount END),0) AS revenue, "
                + "CASE WHEN e.seating_type = 'RESERVED' THEN (SELECT COUNT(*) FROM event_seats s "
                + "  WHERE s.event_id = e.id AND s.booking_id IS NOT NULL) END AS seats_taken "
                + "FROM events e JOIN venues v ON v.id = e.venue_id LEFT JOIN bookings b ON b.event_id = e.id "
                + (includeArchived ? "" : "WHERE e.archived_at IS NULL ")
                + "GROUP BY e.id, e.title, v.city, e.starts_at, e.status, e.archived_at, e.capacity, e.tickets_sold, e.seating_type "
                + "ORDER BY e.starts_at";
        return queryList(conn, sql, rs -> new InventoryRow(rs.getLong("id"), rs.getString("title"), rs.getString("city"),
                dateTime(rs, "starts_at"), rs.getString("status"), rs.getObject("archived_at") != null,
                rs.getInt("capacity"), rs.getInt("tickets_sold"), rs.getLong("confirmed_tickets"), rs.getLong("held_tickets"),
                nullableLong(rs, "seats_taken"), rs.getBigDecimal("revenue")));
    }
}
