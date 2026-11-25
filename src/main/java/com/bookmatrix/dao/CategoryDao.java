package com.bookmatrix.dao;

import com.bookmatrix.db.JdbcDao;
import com.bookmatrix.model.Category;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class CategoryDao extends JdbcDao {

    public List<Category> findAll(Connection conn) throws SQLException {
        return queryList(conn, "SELECT id, name, slug, description FROM categories ORDER BY name", CategoryDao::map);
    }

    public Optional<Category> findById(Connection conn, int id) throws SQLException {
        return queryOne(conn, "SELECT id, name, slug, description FROM categories WHERE id = ?", CategoryDao::map, id);
    }

    public Optional<Category> findByName(Connection conn, String name) throws SQLException {
        return queryOne(conn, "SELECT id, name, slug, description FROM categories WHERE name = ?", CategoryDao::map, name);
    }

    public int insert(Connection conn, String name, String slug, String description) throws SQLException {
        return (int) insert(conn, "INSERT INTO categories (name, slug, description) VALUES (?, ?, ?)", name, slug, description);
    }

    /** Number of events in each category, used for the category navigation on the home page. */
    public List<Object[]> countUpcomingPerCategory(Connection conn, java.time.LocalDateTime now) throws SQLException {
        return queryList(conn,
                "SELECT c.id, COUNT(e.id) FROM categories c LEFT JOIN events e ON e.category_id = c.id "
              + " AND e.archived_at IS NULL AND e.status = 'SCHEDULED' AND e.starts_at > ? GROUP BY c.id",
                rs -> new Object[]{rs.getInt(1), rs.getLong(2)}, now);
    }

    private static Category map(ResultSet rs) throws SQLException {
        return new Category(rs.getInt("id"), rs.getString("name"), rs.getString("slug"), rs.getString("description"));
    }
}
