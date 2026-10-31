package kz.jondeu.estimates;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "estimates")
@Getter @Setter @NoArgsConstructor
public class Estimate {
    @Id private UUID id = UUID.randomUUID();
    private UUID projectId;
    private String number;
    private int version = 1;
    private UUID contractorId;
    @Enumerated(EnumType.STRING) private EstimateStatus status = EstimateStatus.DRAFT;
    private String currency = "KZT";
    private BigDecimal additionalCosts = BigDecimal.ZERO.setScale(2);
    private BigDecimal discount = BigDecimal.ZERO.setScale(2);
    private BigDecimal taxRate = BigDecimal.ZERO.setScale(2);
    private BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
    private BigDecimal taxAmount = BigDecimal.ZERO.setScale(2);
    private BigDecimal total = BigDecimal.ZERO.setScale(2);
    private String reviewComment;
    private Instant approvedAt;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
