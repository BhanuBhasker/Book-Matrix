package com.bookmatrix.demo;

import java.io.PrintStream;

/** What a demo needs: where to print, and (for the JDBC demo) how to reach MySQL. */
public record DemoContext(PrintStream out, String dbUrl, String dbUser, String dbPassword) {

    public boolean hasDatabase() {
        return dbUrl != null && !dbUrl.isBlank();
    }
}
