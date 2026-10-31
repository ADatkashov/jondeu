package kz.jondeu.estimates;

import java.math.BigDecimal;
import java.util.List;
import kz.jondeu.common.Money;

/**
 * Central, decimal-safe estimate math. The backend is the only authority on totals:
 * line total = round(qty * unitPrice); subtotal = Σ lines; taxable = max(0, subtotal + additional - discount);
 * tax = round(taxable * rate / 100); total = taxable + tax.
 */
public final class EstimateCalculator {
    private EstimateCalculator() {}

    public record Result(BigDecimal subtotal, BigDecimal taxAmount, BigDecimal total) {}

    public static BigDecimal lineTotal(BigDecimal quantity, BigDecimal unitPrice) { return Money.mul(quantity, unitPrice); }

    public static Result calculate(List<BigDecimal> lineTotals, BigDecimal additional, BigDecimal discount, BigDecimal taxRate) {
        BigDecimal subtotal = lineTotals.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal taxable = subtotal.add(Money.nz(additional)).subtract(Money.nz(discount));
        if (taxable.signum() < 0) taxable = BigDecimal.ZERO;
        BigDecimal tax = Money.percent(taxable, taxRate == null ? BigDecimal.ZERO : taxRate);
        return new Result(Money.scale(subtotal), tax, Money.scale(taxable.add(tax)));
    }
}
