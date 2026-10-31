package kz.jondeu.payments;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payments")
@Getter @Setter @NoArgsConstructor
public class Payment {
    @Id private UUID id = UUID.randomUUID();
    private UUID projectId;
    private UUID stageId;
    private UUID payerId;
    private UUID requestedBy;
    @Enumerated(EnumType.STRING) private PaymentEnums.Type type;
    @Enumerated(EnumType.STRING) private PaymentEnums.Method method;
    private BigDecimal amount;
    private String currency = "KZT";
    @Enumerated(EnumType.STRING) private PaymentEnums.Status status;
    private String provider;
    private String providerRef;
    private String reference;
    private String idempotencyKey;
    private String note;
    private Instant createdAt = Instant.now();
    private Instant paidAt;
}
