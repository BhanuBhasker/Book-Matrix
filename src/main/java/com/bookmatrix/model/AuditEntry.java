package com.bookmatrix.model;

import java.time.LocalDateTime;

/** One row of the audit_log table. */
public record AuditEntry(long id, Long actorUserId, String actorName, String action,
                         String entityType, Long entityId, String details, LocalDateTime createdAt) {
}
