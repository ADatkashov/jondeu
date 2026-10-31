package kz.jondeu.payments;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import kz.jondeu.common.Money;
import org.springframework.stereotype.Service;

/**
 * Single source of truth for project money. Net paid is derived from the append-only ledger;
 * reserved is money in flight (PENDING payments) so concurrent payments cannot overshoot the budget.
 */
@Service
public class FinanceService {
    public record Position(BigDecimal approvedBudget, BigDecimal paid, BigDecimal reserved, BigDecimal remaining, BigDecimal available) {}

    private final LedgerRepository ledger;
    private final PaymentRepository payments;

    public FinanceService(LedgerRepository ledger, PaymentRepository payments) { this.ledger = ledger; this.payments = payments; }

    public BigDecimal netPaid(UUID projectId) { return Money.nz(ledger.net(projectId)); }

    public BigDecimal reserved(UUID projectId) { return Money.nz(payments.sumByStatuses(projectId, List.of(PaymentEnums.Status.PENDING))); }

    public Position position(UUID projectId, BigDecimal approvedBudget) {
        BigDecimal paid = netPaid(projectId);
        BigDecimal reserved = reserved(projectId);
        BigDecimal remaining = Money.scale(approvedBudget.subtract(paid)).max(Money.ZERO);
        BigDecimal available = Money.scale(approvedBudget.subtract(paid).subtract(reserved)).max(Money.ZERO);
        return new Position(Money.scale(approvedBudget), paid, reserved, remaining, available);
    }
}
