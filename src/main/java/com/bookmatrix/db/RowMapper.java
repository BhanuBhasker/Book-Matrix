package com.bookmatrix.db;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Converts the CURRENT row of a ResultSet into a Java object (Java object mapping). */
@FunctionalInterface
public interface RowMapper<T> {
    T map(ResultSet rs) throws SQLException;
}
