package com.bookmatrix.dao;

import com.bookmatrix.db.Database;
import com.bookmatrix.db.JdbcDao;
import com.bookmatrix.model.Venue;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class VenueDao extends JdbcDao {

    public Optional<Venue> findByNameAndCity(Connection conn, String name, String city) throws SQLException {
        return queryOne(conn, "SELECT id, name, city, address FROM venues WHERE name = ? AND city = ?",
                VenueDao::map, name, city);
    }

    /** Re-uses an existing venue (same name + city) or creates it. Keeps venues normalised. */
    public Venue findOrCreate(Connection conn, String name, String city, String address, LocalDateTime now)
            throws SQLException {
        Optional<Venue> existing = findByNameAndCity(conn, name, city);
        if (existing.isPresent()) {
            Venue venue = existing.get();
            if (address != null && !address.equals(venue.address())) {
                update(conn, "UPDATE venues SET address = ? WHERE id = ?", address, venue.id());
                return new Venue(venue.id(), venue.name(), venue.city(), address);
            }
            return venue;
        }
        try {
            int id = (int) insert(conn, "INSERT INTO venues (name, city, address, created_at) VALUES (?, ?, ?, ?)",
                    name, city, address, now);
            return new Venue(id, name, city, address);
        } catch (SQLException e) {
            if (Database.isDuplicateKey(e)) {   // someone created it at the same moment
                return findByNameAndCity(conn, name, city).orElseThrow(() -> e);
            }
            throw e;
        }
    }

    public List<String> distinctCities(Connection conn) throws SQLException {
        return queryList(conn,
                "SELECT DISTINCT v.city FROM venues v JOIN events e ON e.venue_id = v.id "
              + "WHERE e.archived_at IS NULL ORDER BY v.city", rs -> rs.getString(1));
    }

    private static Venue map(ResultSet rs) throws SQLException {
        return new Venue(rs.getInt("id"), rs.getString("name"), rs.getString("city"), rs.getString("address"));
    }
}
