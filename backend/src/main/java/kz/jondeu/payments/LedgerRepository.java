package kz.jondeu.payments;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LedgerRepository extends JpaRepository<LedgerEntry, UUID> {
    @Query("select coalesce(sum(case when l.kind = 'PAYMENT' then l.amount else -l.amount end), 0) from LedgerEntry l where l.projectId = :pid")
    BigDecimal net(UUID pid);

    java.util.List<LedgerEntry> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
