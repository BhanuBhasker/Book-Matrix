package com.bookmatrix.dao;

import com.bookmatrix.db.JdbcDao;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** SQL for event_seats: the physical seats of RESERVED-seating events. */
@Repository
public class SeatDao extends JdbcDao {

    /** A seat as stored: row letter + number, and whether some booking holds it. */
    public record SeatRow(String label, boolean taken) { }

    /**
     * Creates every seat for an event (rows A.., seats 1..N) with JDBC BATCHING:
     * addBatch() queues each INSERT and executeBatch() sends them to MySQL together.
     */
    public int generateSeats(Connection conn, long eventId, int rows, int seatsPerRow) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO event_seats (event_id, row_label, seat_number) VALUES (?, ?, ?)")) {
            for (int r = 0; r < rows; r++) {
                for (int n = 1; n <= seatsPerRow; n++) {
                    ps.setLong(1, eventId);
                    ps.setString(2, String.valueOf((char) ('A' + r)));
                    ps.setInt(3, n);
                    ps.addBatch();
                }
            }
            return ps.executeBatch().length;
        }
    }

    public int deleteSeats(Connection conn, long eventId) throws SQLException {
        return update(conn, "DELETE FROM event_seats WHERE event_id = ?", eventId);
    }

    public List<SeatRow> seatMap(Connection conn, long eventId) throws SQLException {
        return queryList(conn, "SELECT row_label, seat_number, booking_id FROM event_seats WHERE event_id = ? "
                        + "ORDER BY row_label, seat_number",
                rs -> new SeatRow(rs.getString("row_label") + rs.getInt("seat_number"), rs.getObject("booking_id") != null),
                eventId);
    }

    /**
     * Gives the requested seats to a booking in ONE conditional UPDATE. "booking_id IS NULL" means
     * only free seats change. If any requested seat was already taken, fewer rows change than
     * requested — the caller then rolls the whole transaction back. Two people can therefore never
     * end up with the same seat.
     */
    public int reserve(Connection conn, long eventId, long bookingId, List<SeatLabel> seats) throws SQLException {
        StringBuilder sql = new StringBuilder("UPDATE event_seats SET booking_id = ? WHERE event_id = ? AND booking_id IS NULL AND (");
        List<Object> params = new ArrayList<>(List.of(bookingId, eventId));
        for (int i = 0; i < seats.size(); i++) {
            sql.append(i == 0 ? "" : " OR ").append("(row_label = ? AND seat_number = ?)");
            params.add(seats.get(i).row());
            params.add(seats.get(i).number());
        }
        sql.append(')');
        return update(conn, sql.toString(), params.toArray());
    }

    /** Frees every seat of a booking (cancellation or expired hold). */
    public int releaseForBooking(Connection conn, long bookingId) throws SQLException {
        return update(conn, "UPDATE event_seats SET booking_id = NULL WHERE booking_id = ?", bookingId);
    }

    public int releaseForEvent(Connection conn, long eventId) throws SQLException {
        return update(conn, "UPDATE event_seats SET booking_id = NULL WHERE event_id = ?", eventId);
    }

    public long countTaken(Connection conn, long eventId) throws SQLException {
        return queryLong(conn, "SELECT COUNT(*) FROM event_seats WHERE event_id = ? AND booking_id IS NOT NULL", eventId);
    }

    /** A parsed seat label such as "C12" -> row "C", number 12. */
    public record SeatLabel(String row, int number) {

        public static SeatLabel parse(String label) {
            String t = label == null ? "" : label.trim().toUpperCase();
            if (!t.matches("^[A-Z][1-9][0-9]?$")) {
                throw new IllegalArgumentException("Invalid seat '" + label + "'");
            }
            return new SeatLabel(t.substring(0, 1), Integer.parseInt(t.substring(1)));
        }

        @Override
        public String toString() {
            return row + number;
        }
    }
}
