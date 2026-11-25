package com.bookmatrix.db;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a dynamic WHERE clause for search screens WITHOUT string-concatenating user input.
 * Only fixed SQL fragments (written by us) are joined; user values go into the params list
 * and are later bound to "?" placeholders.
 */
public class SqlWhere {

    private final List<String> clauses = new ArrayList<>();
    private final List<Object> params = new ArrayList<>();

    public SqlWhere add(String clause, Object... values) {
        clauses.add(clause);
        params.addAll(List.of(values));
        return this;    // returning "this" allows chaining: where.add(..).add(..)
    }

    public String toSql() {
        return clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
    }

    public Object[] params() {
        return params.toArray();
    }

    /** Params followed by extra values (e.g. LIMIT and OFFSET). */
    public Object[] paramsWith(Object... extra) {
        List<Object> all = new ArrayList<>(params);
        all.addAll(List.of(extra));
        return all.toArray();
    }
}
