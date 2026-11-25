package com.bookmatrix.demo;

import java.io.PrintStream;

/**
 * Every syllabus demonstration implements this interface. DemoRunner (command line) and the
 * admin "Java Lab" page treat all demos the same way through this type — that is polymorphism.
 */
public interface ConceptDemo {

    /** Short id used on the command line, e.g. "collections". */
    String name();

    String title();

    /** Comma separated syllabus topics covered. */
    String topics();

    void run(DemoContext ctx) throws Exception;

    /** Default method: shared helper every demo gets for free. */
    default void section(PrintStream out, String heading) {
        out.println();
        out.println("── " + heading + " " + "─".repeat(Math.max(3, 60 - heading.length())));
    }
}
