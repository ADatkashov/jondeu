package kz.jondeu.stages;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProgressCalculatorTest {
    private static Stage st(int weight, int progress) {
        Stage s = new Stage();
        s.setWeight(new BigDecimal(weight));
        s.setProgress(progress);
        return s;
    }

    @Test
    void usesWeightsNotSimpleAverage() {
        // simple average would be 50.0, weighted is (90*100 + 10*0)/100 = 90.0
        assertThat(ProgressCalculator.weighted(List.of(st(90, 100), st(10, 0)))).isEqualByComparingTo("90.0");
        assertThat(ProgressCalculator.weighted(List.of(st(10, 100), st(90, 0)))).isEqualByComparingTo("10.0");
    }

    @Test
    void emptyPlanIsZero() { assertThat(ProgressCalculator.weighted(List.of())).isEqualByComparingTo("0"); }

    @Test
    void roundsToOneDecimal() {
        assertThat(ProgressCalculator.weighted(List.of(st(1, 100), st(1, 0), st(1, 0)))).isEqualByComparingTo("33.3");
    }
}
