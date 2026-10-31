package kz.jondeu.stages;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Weighted project progress: sum(weight * progress) / sum(weight). Never a plain average when weights exist. */
public final class ProgressCalculator {
    private ProgressCalculator() {}

    public static BigDecimal weighted(List<Stage> stages) {
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal acc = BigDecimal.ZERO;
        for (Stage s : stages) {
            BigDecimal w = s.getWeight() == null ? BigDecimal.ONE : s.getWeight();
            totalWeight = totalWeight.add(w);
            acc = acc.add(w.multiply(BigDecimal.valueOf(s.getProgress())));
        }
        if (totalWeight.signum() == 0) return BigDecimal.ZERO.setScale(1);
        return acc.divide(totalWeight, 1, RoundingMode.HALF_UP);
    }
}
