package kz.jondeu.estimates;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class EstimateCalculatorTest {
    private static BigDecimal d(String s) { return new BigDecimal(s); }

    @Test
    void referenceEstimateFromSpecSumsExactly() {
        var r = EstimateCalculator.calculate(List.of(d("80000"), d("250000"), d("180000"), d("160000"), d("120000"), d("350000")), d("0"), d("0"), d("0"));
        assertThat(r.subtotal()).isEqualByComparingTo("1140000.00");
        assertThat(r.total()).isEqualByComparingTo("1140000.00");
    }

    @Test
    void lineTotalRoundsHalfUpToTwoDecimals() {
        assertThat(EstimateCalculator.lineTotal(d("3"), d("0.335"))).isEqualByComparingTo("1.01"); // 1.005 -> 1.01
        assertThat(EstimateCalculator.lineTotal(d("1.5"), d("100.10"))).isEqualByComparingTo("150.15");
    }

    @Test
    void noFloatingPointDriftWithManySmallLines() {
        List<BigDecimal> lines = java.util.stream.IntStream.range(0, 1000).mapToObj(i -> EstimateCalculator.lineTotal(d("1"), d("0.10"))).toList();
        assertThat(EstimateCalculator.calculate(lines, d("0"), d("0"), d("0")).total()).isEqualByComparingTo("100.00");
    }

    @Test
    void additionalCostsDiscountAndTaxAreApplied() {
        var r = EstimateCalculator.calculate(List.of(d("1000000")), d("50000"), d("100000"), d("12"));
        assertThat(r.subtotal()).isEqualByComparingTo("1000000.00");
        assertThat(r.taxAmount()).isEqualByComparingTo("114000.00"); // 12% of 950 000
        assertThat(r.total()).isEqualByComparingTo("1064000.00");
    }

    @Test
    void discountLargerThanSubtotalNeverProducesNegativeTotal() {
        var r = EstimateCalculator.calculate(List.of(d("1000")), d("0"), d("5000"), d("12"));
        assertThat(r.total()).isEqualByComparingTo("0.00");
    }
}
