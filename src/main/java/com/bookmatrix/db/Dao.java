package com.bookmatrix.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Generic interface implemented by the DAOs: T is the entity type, ID the key type.
 * e.g. UserDao implements Dao<User, Long>.
 */
public interface Dao<T, ID> {
    Optional<T> findById(Connection conn, ID id) throws SQLException;
}
