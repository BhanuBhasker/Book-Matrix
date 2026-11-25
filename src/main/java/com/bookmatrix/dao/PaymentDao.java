package com.bookmatrix.dao;

import com.bookmatrix.db.JdbcDao;
import com.bookmatrix.db.SqlWhere;
import com.bookmatrix.model.Payment;
import com.bookmatrix.util.PageRequest;
import com.bookmatrix.util.TextUtils;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** SQL for the payments table (charges and refunds from the demo gateway). */
@Repository
public class PaymentDao extends JdbcDao {

    private static final String SELECT = "SELECT p.*, b.booking_ref FROM payments p JOIN bookings b ON b.id = p.booking_id";

    public long insert(Connection conn, long bookingId, Payment.Kind kind, Payment.Status status, BigDecimal amount,
                       Payment.Method method, String cardBrand, String cardLast4, String upiHandle, String gatewayRef,
                       String failureReason, LocalDateTime now) throws SQLException {
        return insert(conn, "INSERT INTO payments (booking_id, kind, status, amount, method, card_brand, card_last4, upi_handle, "
                        + "gateway_ref, failure_reason, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                bookingId, kind, status, amount, method, cardBrand, cardLast4, upiHandle, gatewayRef, failureReason, now);
    }

    public List<Payment> findForBooking(Connection conn, long bookingId) throws SQLException {
        return queryList(conn, SELECT + " WHERE p.booking_id = ? ORDER BY p.id", PaymentDao::map, bookingId);
    }

    public Optional<Payment> findSuccessfulCharge(Connection conn, long bookingId) throws SQLException {
        return queryOne(conn, SELECT + " WHERE p.booking_id = ? AND p.kind = 'CHARGE' AND p.status = 'SUCCEEDED'",
                PaymentDao::map, bookingId);
    }

    /** Refunds every paid, still-confirmed booking of an event in one INSERT ... SELECT (used when an event is cancelled). */
    public int refundAllForEvent(Connection conn, long eventId, LocalDateTime now) throws SQLException {
        return update(conn, "INSERT INTO payments (booking_id, kind, status, amount, method, card_brand, card_last4, upi_handle, "
                + "gateway_ref, created_at) SELECT p.booking_id, 'REFUND', 'SUCCEEDED', p.amount, p.method, p.card_brand, "
                + "p.card_last4, p.upi_handle, CONCAT('RF-', b.booking_ref), ? FROM payments p JOIN bookings b ON b.id = p.booking_id "
                + "WHERE b.event_id = ? AND b.status = 'CONFIRMED' AND p.kind = 'CHARGE' AND p.status = 'SUCCEEDED'", now, eventId);
    }

    public List<Payment> search(Connection conn, String text, String kind, String status, PageRequest page) throws SQLException {
        SqlWhere where = where(text, kind, status);
        return queryList(conn, SELECT + where.toSql() + " ORDER BY p.id DESC LIMIT ? OFFSET ?", PaymentDao::map,
                where.paramsWith(page.size(), page.offset()));
    }

    public long count(Connection conn, String text, String kind, String status) throws SQLException {
        SqlWhere where = where(text, kind, status);
        return queryLong(conn, "SELECT COUNT(*) FROM payments p JOIN bookings b ON b.id = p.booking_id" + where.toSql(), where.params());
    }

    private SqlWhere where(String text, String kind, String status) {
        SqlWhere where = new SqlWhere();
        if (!TextUtils.isBlank(text)) {
            String like = "%" + TextUtils.escapeLike(text) + "%";
            where.add("(b.booking_ref LIKE ? OR p.gateway_ref LIKE ? OR p.card_last4 LIKE ? OR p.upi_handle LIKE ?)",
                    like, like, like, like);
        }
        if ("CHARGE".equals(kind) || "REFUND".equals(kind)) {
            where.add("p.kind = ?", kind);
        }
        if ("SUCCEEDED".equals(status) || "FAILED".equals(status)) {
            where.add("p.status = ?", status);
        }
        return where;
    }

    static Payment map(ResultSet rs) throws SQLException {
        return new Payment(rs.getLong("id"), rs.getLong("booking_id"), rs.getString("booking_ref"),
                Payment.Kind.valueOf(rs.getString("kind")), Payment.Status.valueOf(rs.getString("status")),
                rs.getBigDecimal("amount"), Payment.Method.valueOf(rs.getString("method")), rs.getString("card_brand"),
                rs.getString("card_last4"), rs.getString("upi_handle"), rs.getString("gateway_ref"),
                rs.getString("failure_reason"), dateTime(rs, "created_at"));
    }
}
