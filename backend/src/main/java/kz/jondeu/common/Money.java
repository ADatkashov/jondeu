package kz.jondeu.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Decimal-safe money helpers. No floating point anywhere in financial code. */
public final class Money {
    public static final String KZT = "KZT";
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private Money() {}

    public static BigDecimal scale(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal mul(BigDecimal qty, BigDecimal price) {
        return qty.multiply(price).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal percent(BigDecimal base, BigDecimal rate) {
        return base.multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal nz(BigDecimal v) { return v == null ? ZERO : scale(v); }
}
