package com.bookmatrix.demo;

import com.bookmatrix.util.BookingReferenceGenerator;
import com.bookmatrix.util.Money;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Strings, StringBuilder, StringBuffer and wrapper classes. */
public class StringsDemo implements ConceptDemo {

    @Override public String name() { return "strings"; }
    @Override public String title() { return "Strings, StringBuilder, StringBuffer and wrapper classes"; }
    @Override public String topics() { return "String immutability, String pool, == vs equals, Comparison, Searching, Manipulation, Formatting, StringBuilder, StringBuffer, Integer, Double, Boolean, Parsing, Autoboxing, Unboxing"; }

    @Override
    public void run(DemoContext ctx) throws InterruptedException {
        PrintStream out = ctx.out();

        section(out, "Immutability");
        String title = "indie night";
        String upper = title.toUpperCase(Locale.ROOT);
        out.println("title.toUpperCase() returned \"" + upper + "\" but title is still \"" + title + "\"");

        section(out, "String pool, == versus equals()");
        String a = "BookMatrix";                    // literal -> pooled
        String b = "BookMatrix";                    // same pooled object
        String c = new String("BookMatrix");        // a NEW object on the heap
        out.println("a == b        : " + (a == b) + "   (same object from the pool)");
        out.println("a == c        : " + (a == c) + "  (different objects)");
        out.println("a.equals(c)   : " + a.equals(c) + "   (same characters)");
        out.println("a == c.intern(): " + (a == c.intern()) + "   (intern() returns the pooled copy)");

        section(out, "Comparison");
        out.println("\"apple\".compareTo(\"banana\") = " + "apple".compareTo("banana") + " (negative: apple sorts first)");
        out.println("\"PUNE\".equalsIgnoreCase(\"pune\") = " + "PUNE".equalsIgnoreCase("pune"));

        section(out, "Searching");
        String description = "An evening of acoustic music at the Blue Frog, Pune.";
        out.println("contains(\"music\")     = " + description.contains("music"));
        out.println("indexOf(\"Blue Frog\")  = " + description.indexOf("Blue Frog"));
        out.println("startsWith(\"An\")      = " + description.startsWith("An"));
        out.println("endsWith(\"Pune.\")     = " + description.endsWith("Pune."));

        section(out, "Manipulation");
        String messy = "   Comedy   Night   ";
        out.println("strip() + collapse spaces -> \"" + messy.strip().replaceAll("\\s+", " ") + "\"");
        out.println("substring(0, 10)         -> \"" + description.substring(0, 10) + "\"");
        String[] parts = description.split(", ");
        out.println("split(\", \") -> " + parts.length + " parts: \"" + String.join("\" and \"", parts) + "\"");
        out.println("String.join(\" | \", ...)  -> " + String.join(" | ", "Music", "Comedy", "Theatre"));

        section(out, "Formatting");
        out.println(String.format("%-20s|%8s|%6d", "Indie Night", "Pune", 150));
        out.println("Money.format(1499.5) -> " + Money.format(new BigDecimal("1499.5")));

        section(out, "StringBuilder (used for booking references and receipts)");
        StringBuilder sb = new StringBuilder("Receipt");
        sb.append(" #").append(42).append(" - ").append("2 tickets");
        sb.insert(0, "[").append("]");
        out.println(sb + "  (one mutable object, no new String per append)");
        out.println("A real booking reference: " + BookingReferenceGenerator.next());

        section(out, "StringBuffer (thread-safe: its methods are synchronized)");
        StringBuffer log = new StringBuffer();
        Thread t1 = new Thread(() -> { for (int i = 0; i < 1000; i++) log.append('A'); });
        Thread t2 = new Thread(() -> { for (int i = 0; i < 1000; i++) log.append('B'); });
        t1.start(); t2.start();
        t1.join(); t2.join();
        out.println("Two threads appended 1000 chars each -> length " + log.length() + " (always 2000)");
        out.println("StringBuilder is faster but NOT synchronized; use it when only one thread writes.");

        section(out, "Wrapper classes: parsing, autoboxing, unboxing");
        int quantity = Integer.parseInt("3");              // String -> int
        double price = Double.parseDouble("799.50");        // String -> double
        boolean featured = Boolean.parseBoolean("TRUE");    // String -> boolean (case-insensitive)
        out.println("Integer.parseInt(\"3\")=" + quantity + ", Double.parseDouble(\"799.50\")=" + price
                + ", Boolean.parseBoolean(\"TRUE\")=" + featured);
        List<Integer> quantities = new ArrayList<>();
        quantities.add(quantity);                           // autoboxing int -> Integer
        int first = quantities.get(0);                      // unboxing Integer -> int
        out.println("Collections hold objects, so 3 was autoboxed to Integer and unboxed back: " + first);
        Integer small1 = 127, small2 = 127, big1 = 1000, big2 = 1000;
        out.println("Integer 127 == 127: " + (small1 == small2) + " (cached), 1000 == 1000: " + (big1 == big2)
                + " -> always compare wrappers with equals(): " + big1.equals(big2));
        Integer missing = null;
        try {
            int boom = missing;     // unboxing null
            out.println(boom);
        } catch (NullPointerException e) {
            out.println("Unboxing a null Integer throws NullPointerException - BookMatrix checks for null first.");
        }
        out.println("Integer.MAX_VALUE=" + Integer.MAX_VALUE + ", Double.compare(0.1+0.2, 0.3)=" + Double.compare(0.1 + 0.2, 0.3)
                + " (that is why money uses BigDecimal)");
    }
}
