package com.bookmatrix.dao;

import com.bookmatrix.db.Dao;
import com.bookmatrix.db.JdbcDao;
import com.bookmatrix.db.SqlWhere;
import com.bookmatrix.dto.EventLifecycleFilter;
import com.bookmatrix.dto.EventSearchCriteria;
import com.bookmatrix.model.Event;
import com.bookmatrix.model.EventStatus;
import com.bookmatrix.model.SeatingType;
import com.bookmatrix.util.PageRequest;
import com.bookmatrix.util.TextUtils;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** All SQL for events, including the atomic inventory updates. */
@Repository
public class EventDao extends JdbcDao implements Dao<Event, Long> {

    private static final String SELECT =
            "SELECT e.id, e.title, e.description, e.category_id, e.venue_id, e.starts_at, e.ends_at, "
          + "e.ticket_price, e.capacity, e.tickets_sold, e.seating_type, e.seat_rows, e.seats_per_row, e.image_url, e.is_featured, e.status, "
          + "e.cancelled_at, e.archived_at, e.created_at, e.updated_at, "
          + "c.name AS category_name, v.name AS venue_name, v.city, v.address AS venue_address "
          + "FROM events e JOIN categories c ON c.id = e.category_id JOIN venues v ON v.id = e.venue_id";

    @Override
    public Optional<Event> findById(Connection conn, Long id) throws SQLException {
        return queryOne(conn, SELECT + " WHERE e.id = ?", EventDao::map, id);
    }

    /**
     * Reads the event AND locks its row until the transaction commits/rolls back.
     * Other transactions that want to book the same event wait here, one at a time.
     */
    public Optional<Event> findByIdForUpdate(Connection conn, long id) throws SQLException {
        return queryOne(conn, SELECT + " WHERE e.id = ? FOR UPDATE OF e", EventDao::map, id);
    }

    public List<Event> search(Connection conn, EventSearchCriteria criteria, PageRequest page, LocalDateTime now)
            throws SQLException {
        SqlWhere where = buildWhere(criteria, now);
        String sql = SELECT + where.toSql() + " ORDER BY " + criteria.sort().orderBy() + " LIMIT ? OFFSET ?";
        return queryList(conn, sql, EventDao::map, where.paramsWith(page.size(), page.offset()));
    }

    public long count(Connection conn, EventSearchCriteria criteria, LocalDateTime now) throws SQLException {
        SqlWhere where = buildWhere(criteria, now);
        return queryLong(conn, "SELECT COUNT(*) FROM events e JOIN categories c ON c.id = e.category_id "
                + "JOIN venues v ON v.id = e.venue_id" + where.toSql(), where.params());
    }

    private SqlWhere buildWhere(EventSearchCriteria c, LocalDateTime now) {
        SqlWhere where = new SqlWhere();
        switch (c.archive()) {
            case EXCLUDE -> where.add("e.archived_at IS NULL");
            case ONLY -> where.add("e.archived_at IS NOT NULL");
            case INCLUDE -> { }
        }
        (c.lifecycle() == null ? EventLifecycleFilter.ALL : c.lifecycle()).apply(where, now);
        if (!TextUtils.isBlank(c.text())) {
            // one search box matches title, venue, city, category and description
            String like = "%" + TextUtils.escapeLike(c.text()) + "%";
            where.add("(e.title LIKE ? OR v.name LIKE ? OR v.city LIKE ? OR c.name LIKE ? OR e.description LIKE ?)",
                    like, like, like, like, like);
        }
        if (c.categoryId() != null) {
            where.add("e.category_id = ?", c.categoryId());
        }
        if (!TextUtils.isBlank(c.city())) {
            where.add("v.city = ?", c.city());
        }
        if (c.dateFrom() != null) {
            where.add("e.starts_at >= ?", c.dateFrom().atStartOfDay());
        }
        if (c.dateTo() != null) {
            where.add("e.starts_at < ?", c.dateTo().plusDays(1).atStartOfDay());
        }
        if (c.minPrice() != null) {
            where.add("e.ticket_price >= ?", c.minPrice());
        }
        if (c.maxPrice() != null) {
            where.add("e.ticket_price <= ?", c.maxPrice());
        }
        if (c.featuredOnly()) {
            where.add("e.is_featured = TRUE");
        }
        return where;
    }

    public long insert(Connection conn, Event e, long createdBy, LocalDateTime now) throws SQLException {
        return insert(conn,
                "INSERT INTO events (title, description, category_id, venue_id, starts_at, ends_at, ticket_price, "
              + "capacity, tickets_sold, seating_type, seat_rows, seats_per_row, image_url, is_featured, status, created_by, "
              + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, 'SCHEDULED', ?, ?, ?)",
                e.getTitle(), e.getDescription(), e.getCategoryId(), e.getVenueId(), e.getStartsAt(), e.getEndsAt(),
                e.getTicketPrice(), e.getCapacity(), e.getSeatingType(), e.getSeatRows(), e.getSeatsPerRow(),
                e.getImageUrl(), e.isFeatured(), createdBy, now, now);
    }

    /**
     * Updates editable fields. "AND tickets_sold <= ?" makes the database itself refuse a
     * capacity lower than the tickets already sold (returns 0 rows changed).
     */
    public int update(Connection conn, Event e, LocalDateTime now) throws SQLException {
        return update(conn,
                "UPDATE events SET title = ?, description = ?, category_id = ?, venue_id = ?, starts_at = ?, ends_at = ?, "
              + "ticket_price = ?, capacity = ?, seating_type = ?, seat_rows = ?, seats_per_row = ?, image_url = ?, "
              + "is_featured = ?, updated_at = ? WHERE id = ? AND tickets_sold <= ?",
                e.getTitle(), e.getDescription(), e.getCategoryId(), e.getVenueId(), e.getStartsAt(), e.getEndsAt(),
                e.getTicketPrice(), e.getCapacity(), e.getSeatingType(), e.getSeatRows(), e.getSeatsPerRow(),
                e.getImageUrl(), e.isFeatured(), now, e.getId(), e.getCapacity());
    }

    /**
     * THE core of overselling protection: one atomic statement that only succeeds if there
     * are still enough tickets AND the event is still bookable. MySQL runs the check and the
     * increment together under a row lock, so two requests can never both take the last seat.
     * Returns 1 if the tickets were reserved, 0 if not.
     */
    public int reserveTickets(Connection conn, long eventId, int quantity, LocalDateTime now) throws SQLException {
        return update(conn,
                "UPDATE events SET tickets_sold = tickets_sold + ? "
              + "WHERE id = ? AND status = 'SCHEDULED' AND archived_at IS NULL AND starts_at > ? "
              + "AND tickets_sold + ? <= capacity",
                quantity, eventId, now, quantity);
    }

    /** Gives tickets back. The guard stops inventory from ever going negative. */
    public int releaseTickets(Connection conn, long eventId, int quantity) throws SQLException {
        return update(conn, "UPDATE events SET tickets_sold = tickets_sold - ? WHERE id = ? AND tickets_sold >= ?",
                quantity, eventId, quantity);
    }

    public int markCancelled(Connection conn, long eventId, LocalDateTime now) throws SQLException {
        return update(conn, "UPDATE events SET status = 'CANCELLED', cancelled_at = ?, tickets_sold = 0, updated_at = ? "
                + "WHERE id = ? AND status = 'SCHEDULED'", now, now, eventId);
    }

    public int setArchived(Connection conn, long eventId, LocalDateTime archivedAt, LocalDateTime now) throws SQLException {
        return update(conn, "UPDATE events SET archived_at = ?, updated_at = ? WHERE id = ?", archivedAt, now, eventId);
    }

    public int setFeatured(Connection conn, long eventId, boolean featured) throws SQLException {
        return update(conn, "UPDATE events SET is_featured = ? WHERE id = ?", featured, eventId);
    }

    public int deleteById(Connection conn, long eventId) throws SQLException {
        return update(conn, "DELETE FROM events WHERE id = ?", eventId);
    }

    public boolean existsSlot(Connection conn, String title, int venueId, LocalDateTime startsAt, Long excludeId)
            throws SQLException {
        return queryLong(conn, "SELECT COUNT(*) FROM events WHERE title = ? AND venue_id = ? AND starts_at = ? AND id <> ?",
                title, venueId, startsAt, excludeId == null ? 0L : excludeId) > 0;
    }

    static Event map(ResultSet rs) throws SQLException {
        Event e = new Event();
        e.setId(rs.getLong("id"));
        e.setTitle(rs.getString("title"));
        e.setDescription(rs.getString("description"));
        e.setCategoryId(rs.getInt("category_id"));
        e.setCategoryName(rs.getString("category_name"));
        e.setVenueId(rs.getInt("venue_id"));
        e.setVenueName(rs.getString("venue_name"));
        e.setCity(rs.getString("city"));
        e.setVenueAddress(rs.getString("venue_address"));
        e.setStartsAt(dateTime(rs, "starts_at"));
        e.setEndsAt(dateTime(rs, "ends_at"));
        e.setTicketPrice(rs.getBigDecimal("ticket_price"));
        e.setCapacity(rs.getInt("capacity"));
        e.setTicketsSold(rs.getInt("tickets_sold"));
        e.setSeatingType(SeatingType.valueOf(rs.getString("seating_type")));
        e.setSeatRows(rs.getObject("seat_rows") == null ? null : rs.getInt("seat_rows"));
        e.setSeatsPerRow(rs.getObject("seats_per_row") == null ? null : rs.getInt("seats_per_row"));
        e.setImageUrl(rs.getString("image_url"));
        e.setFeatured(rs.getBoolean("is_featured"));
        e.setStatus(EventStatus.valueOf(rs.getString("status")));
        e.setCancelledAt(dateTime(rs, "cancelled_at"));
        e.setArchivedAt(dateTime(rs, "archived_at"));
        e.setCreatedAt(dateTime(rs, "created_at"));
        e.setUpdatedAt(dateTime(rs, "updated_at"));
        return e;
    }
}
