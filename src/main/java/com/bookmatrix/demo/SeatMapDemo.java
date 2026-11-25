package com.bookmatrix.demo;

import java.io.PrintStream;
import java.util.Arrays;

/** Multidimensional arrays: initialisation, traversal, searching, updating, display, index validation. */
public class SeatMapDemo implements ConceptDemo {

    @Override public String name() { return "arrays"; }
    @Override public String title() { return "2-D arrays: a theatre seat map"; }
    @Override public String topics() { return "Multidimensional arrays, Initialization, Traversal, Searching, Updating, Display, Index validation"; }

    @Override
    public void run(DemoContext ctx) {
        PrintStream out = ctx.out();
        section(out, "Initialisation: new char[5][8]");
        SeatMap hall = new SeatMap(5, 8);
        out.println("Free seats: " + hall.countFree() + " of " + hall.rows() * hall.columns());

        section(out, "Updating elements: book a few seats");
        int[][] requests = {{0, 2}, {0, 3}, {0, 4}, {1, 0}, {1, 1}, {2, 5}};   // a 2-D array literal
        for (int[] seat : requests) {
            hall.book(seat[0], seat[1]);
        }
        out.println("Booked " + requests.length + " seats: " + Arrays.deepToString(requests));
        out.println("Booking A3 again returns " + hall.book(0, 2) + " (already taken)");

        section(out, "Display");
        out.print(hall.render());

        section(out, "Traversal + searching");
        out.println("Free seats after booking (nested loop count): " + hall.countFree());
        int[] block = hall.findAdjacent(4);
        out.println("First block of 4 adjacent free seats: "
                + (block == null ? "none" : SeatMap.label(block[0]) + "" + (block[1] + 1) + " onwards"));

        section(out, "Index validation");
        try {
            hall.book(7, 20);
        } catch (IndexOutOfBoundsException e) {
            out.println("Caught: " + e.getMessage());
        }

        section(out, "Jagged array (rows of different length)");
        int[][] balcony = new int[3][];
        balcony[0] = new int[]{1, 2, 3};
        balcony[1] = new int[]{1, 2, 3, 4, 5};
        balcony[2] = new int[]{1, 2};
        for (int r = 0; r < balcony.length; r++) {
            out.println("Balcony row " + (r + 1) + " has " + balcony[r].length + " seats");
        }
        out.println();
        out.println("Note: the real BookMatrix booking flow uses ticket QUANTITIES (general admission),");
        out.println("protected by the database. This seat map is a separate array demonstration.");
    }
}
