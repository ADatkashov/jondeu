package kz.jondeu.payments;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Append-only financial journal. PAYMENT adds, REFUND subtracts; rows are never updated or deleted. */
@Entity
@Table(name = "ledger_entries")
@Getter @Setter @NoArgsConstructor
public class LedgerEntry {
    public enum Kind { PAYMENT, REFUND }

    @Id private UUID id = UUID.randomUUID();
    private UUID projectId;
    private UUID paymentId;
    @Enumerated(EnumType.STRING) private Kind kind;
    private BigDecimal amount;
    private String currency;
    private UUID actorId;
    private String note;
    private Instant createdAt = Instant.now();
}
