package com.bookmatrix.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money helpers. BigDecimal is used for money because double cannot store 0.1 exactly. */
public final class Money {

    private Money() {
    }

    public static BigDecimal normalize(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    /** String.format based formatting, e.g. 1499.5 -> "Rs. 1,499.50". */
    public static String format(BigDecimal amount) {
        if (amount == null) {
            return "-";
        }
        if (amount.signum() == 0) {
            return "Free";
        }
        return String.format("Rs. %,.2f", amount);
    }
}
