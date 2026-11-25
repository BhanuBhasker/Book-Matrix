package com.bookmatrix.db;

import java.sql.SQLException;

/**
 * A functional interface (exactly one abstract method), so it can be written as a lambda:
 *   db.query(conn -> userDao.findById(conn, 5))
 * Unlike java.util.function.Function, apply() may throw the CHECKED SQLException.
 */
@FunctionalInterface
public interface SqlFunction<T, R> {
    R apply(T input) throws SQLException;
}
