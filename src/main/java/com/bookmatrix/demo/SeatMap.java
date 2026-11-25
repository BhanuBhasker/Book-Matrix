package com.bookmatrix.demo;

/**
 * A theatre seating plan stored in a 2-D array: seats[row][column].
 * '.' = free, 'X' = booked.
 *
 * NOTE: the live BookMatrix site sells general-admission tickets by QUANTITY (no seat numbers),
 * which is what most events need. This class models a seated venue purely to demonstrate
 * multidimensional arrays; it does not affect real bookings.
 */
public class SeatMap {

    public static final char FREE = '.';
    public static final char BOOKED = 'X';

    private final char[][] seats;

    public SeatMap(int rows, int columns) {
        if (rows < 1 || rows > 26 || columns < 1 || columns > 50) {
            throw new IllegalArgumentException("Rows must be 1-26 and columns 1-50");
        }
        seats = new char[rows][columns];                 // initialisation
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                seats[r][c] = FREE;
            }
        }
    }

    public int rows() {
        return seats.length;
    }

    public int columns() {
        return seats[0].length;
    }

    /** Updating one element, with index validation first. */
    public boolean book(int row, int column) {
        validate(row, column);
        if (seats[row][column] == BOOKED) {
            return false;
        }
        seats[row][column] = BOOKED;
        return true;
    }

    public boolean isFree(int row, int column) {
        validate(row, column);
        return seats[row][column] == FREE;
    }

    /** Traversal: visit every element with nested loops. */
    public int countFree() {
        int free = 0;
        for (char[] row : seats) {
            for (char seat : row) {
                if (seat == FREE) {
                    free++;
                }
            }
        }
        return free;
    }

    /** Searching: first row (from the front) with n free seats next to each other. Returns {row, col} or null. */
    public int[] findAdjacent(int n) {
        for (int r = 0; r < seats.length; r++) {
            int run = 0;
            for (int c = 0; c < seats[r].length; c++) {
                run = seats[r][c] == FREE ? run + 1 : 0;
                if (run == n) {
                    return new int[]{r, c - n + 1};
                }
            }
        }
        return null;
    }

    /** Display, e.g. "A  . . X X ." with seat numbers on top. */
    public String render() {
        StringBuilder sb = new StringBuilder("    ");
        for (int c = 0; c < columns(); c++) {
            sb.append(String.format("%-2d", c + 1));
        }
        sb.append('\n');
        for (int r = 0; r < rows(); r++) {
            sb.append(label(r)).append("   ");
            for (int c = 0; c < columns(); c++) {
                sb.append(seats[r][c]).append(' ');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    public static char label(int row) {
        return (char) ('A' + row);
    }

    private void validate(int row, int column) {
        if (row < 0 || row >= seats.length || column < 0 || column >= seats[row].length) {
            throw new IndexOutOfBoundsException("Seat " + (row >= 0 && row < 26 ? label(row) : "?") + (column + 1)
                    + " does not exist in a " + rows() + "x" + columns() + " hall");
        }
    }
}
