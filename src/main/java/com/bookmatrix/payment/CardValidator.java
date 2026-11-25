package com.bookmatrix.payment;

import java.time.YearMonth;
import java.util.Optional;

/** Pure card checks used by the demo gateway (no network, no storage). */
public final class CardValidator {

    private CardValidator() {
    }

    /** Removes spaces and dashes users type: "4242 4242-4242 4242" -> "4242424242424242". */
    public static String digitsOnly(String cardNumber) {
        return cardNumber == null ? "" : cardNumber.replace(" ", "").replace("-", "");
    }

    /**
     * Luhn checksum (used by every real card number): from the right, double every second digit,
     * subtract 9 if the result is above 9, add everything up; valid numbers are divisible by 10.
     */
    public static boolean passesLuhn(String digits) {
        if (digits == null || !digits.matches("\\d{12,19}")) {
            return false;
        }
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }

    /** Card network from the leading digits (IIN ranges, simplified). */
    public static String brand(String digits) {
        if (digits.startsWith("4")) return "Visa";
        if (digits.matches("^(5[1-5]|2[2-7]).*")) return "Mastercard";
        if (digits.matches("^3[47].*")) return "Amex";
        if (digits.matches("^(60|65|81|82|508).*")) return "RuPay";
        return "Card";
    }

    /** Parses "MM/YY" (or "MM/YYYY"). Empty when the format is wrong. */
    public static Optional<YearMonth> parseExpiry(String expiry) {
        if (expiry == null) {
            return Optional.empty();
        }
        String[] parts = expiry.trim().split("/");
        if (parts.length != 2) {
            return Optional.empty();
        }
        try {
            int month = Integer.parseInt(parts[0].trim());
            int year = Integer.parseInt(parts[1].trim());
            if (year < 100) {
                year += 2000;
            }
            if (month < 1 || month > 12) {
                return Optional.empty();
            }
            return Optional.of(YearMonth.of(year, month));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static boolean isValidCvv(String cvv, String brand) {
        return cvv != null && ("Amex".equals(brand) ? cvv.matches("\\d{4}") : cvv.matches("\\d{3}"));
    }

    public static boolean isValidUpi(String handle) {
        return handle != null && handle.matches("^[A-Za-z0-9._-]{2,40}@[A-Za-z]{2,20}$");
    }
}
