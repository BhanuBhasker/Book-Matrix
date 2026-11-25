package com.bookmatrix.dao;

import com.bookmatrix.db.JdbcDao;
import com.bookmatrix.model.AuditEntry;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class AuditLogDao extends JdbcDao {

    public void insert(Connection conn, Long actorId, String action, String entityType, Long entityId,
                       String details, LocalDateTime now) throws SQLException {
        insert(conn, "INSERT INTO audit_log (actor_user_id, action, entity_type, entity_id, details, created_at) "
                   + "VALUES (?, ?, ?, ?, ?, ?)", actorId, action, entityType, entityId, details, now);
    }

    public List<AuditEntry> recent(Connection conn, int limit, String entityType, Long entityId) throws SQLException {
        String sql = "SELECT a.*, u.full_name AS actor_name FROM audit_log a LEFT JOIN users u ON u.id = a.actor_user_id";
        if (entityType != null) {
            return queryList(conn, sql + " WHERE a.entity_type = ? AND a.entity_id = ? ORDER BY a.id DESC LIMIT ?",
                    AuditLogDao::map, entityType, entityId, limit);
        }
        return queryList(conn, sql + " ORDER BY a.id DESC LIMIT ?", AuditLogDao::map, limit);
    }

    private static AuditEntry map(java.sql.ResultSet rs) throws SQLException {
        return new AuditEntry(rs.getLong("id"), nullableLong(rs, "actor_user_id"), rs.getString("actor_name"),
                rs.getString("action"), rs.getString("entity_type"), nullableLong(rs, "entity_id"),
                rs.getString("details"), dateTime(rs, "created_at"));
    }
}
