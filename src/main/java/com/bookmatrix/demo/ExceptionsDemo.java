package com.bookmatrix.demo;

import com.bookmatrix.exception.BookMatrixException;
import com.bookmatrix.exception.CsvFormatException;
import com.bookmatrix.exception.InsufficientTicketsException;
import com.bookmatrix.exception.InvalidBookingException;
import com.bookmatrix.fileio.CsvParser;

import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;

/** try/catch/finally, multi-catch, checked vs unchecked, throw/throws, propagation, custom exceptions. */
public class ExceptionsDemo implements ConceptDemo {

    @Override public String name() { return "exceptions"; }
    @Override public String title() { return "Exception handling"; }
    @Override public String topics() { return "try, catch, finally, Multiple catch, Checked, Unchecked, throw, throws, Propagation, Custom exceptions, try-with-resources, Debugging"; }

    /** A resource that announces when it is opened and closed. */
    static class DemoResource implements AutoCloseable {
        private final String name;
        private final PrintStream out;

        DemoResource(String name, PrintStream out) {
            this.name = name;
            this.out = out;
            out.println("  open  " + name);
        }

        @Override
        public void close() {
            out.println("  close " + name);
        }
    }

    @Override
    public void run(DemoContext ctx) {
        PrintStream out = ctx.out();

        section(out, "try / catch / finally");
        try {
            int quantity = Integer.parseInt("two");
            out.println(quantity);
        } catch (NumberFormatException e) {
            out.println("catch : '" + "two" + "' is not a number -> " + e.getClass().getSimpleName());
        } finally {
            out.println("finally: always runs (used to release resources)");
        }

        section(out, "Multiple catch (one block for several types)");
        for (String input : new String[]{"abc", "io"}) {
            try {
                riskyRead(input);
            } catch (NumberFormatException | IOException e) {
                out.println("caught " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        section(out, "Checked exception (compiler forces handling)");
        try {
            CsvParser.parse(new StringReader("title,city\n\"Unclosed quote,Pune\n"));
        } catch (CsvFormatException e) {
            out.println("CsvFormatException (checked, extends Exception): " + e.getMessage());
        } catch (IOException e) {
            out.println("I/O problem: " + e.getMessage());
        }

        section(out, "Unchecked exception + throw (custom exception)");
        try {
            reserve(1, 3);
        } catch (InsufficientTicketsException e) {
            out.println("InsufficientTicketsException: " + e.getMessage());
            out.println("  is an InvalidBookingException? " + (e instanceof InvalidBookingException));
            out.println("  is a BookMatrixException?      " + (e instanceof BookMatrixException));
            out.println("  is a RuntimeException?         " + (e instanceof RuntimeException) + " (so it is unchecked)");
            out.println("  polymorphic status()/code()   -> HTTP " + e.status().value() + " / " + e.code());
        }

        section(out, "Propagation (a -> b -> c, caught at the top) + debugging with the stack trace");
        try {
            levelA();
        } catch (IllegalStateException e) {
            out.println("Caught in run(): " + e.getMessage());
            out.println("Stack trace shows where it came from:");
            StackTraceElement[] trace = e.getStackTrace();
            for (int i = 0; i < Math.min(3, trace.length); i++) {
                out.println("    at " + trace[i].getMethodName() + "(" + trace[i].getFileName() + ":" + trace[i].getLineNumber() + ")");
            }
            out.println("Cause: " + e.getCause());
        }

        section(out, "try-with-resources (closed automatically, in reverse order)");
        try (DemoResource db = new DemoResource("database connection", out);
             DemoResource file = new DemoResource("CSV file", out)) {
            out.println("  ...working with both resources...");
        }
    }

    /** "throws" declares the checked exception this method may throw. */
    private static void riskyRead(String input) throws IOException {
        if (input.equals("io")) {
            throw new IOException("disk not ready");
        }
        Integer.parseInt(input);
    }

    private static void reserve(int available, int requested) {
        if (requested > available) {
            throw new InsufficientTicketsException(requested, available);   // "throw" raises it
        }
    }

    private static void levelA() {
        levelB();
    }

    private static void levelB() {
        try {
            levelC();
        } catch (ArithmeticException e) {
            throw new IllegalStateException("Could not compute price per ticket", e);   // wrap and re-throw
        }
    }

    private static void levelC() {
        int tickets = 0;
        System.identityHashCode(1499 / tickets);     // division by zero -> ArithmeticException
    }
}
