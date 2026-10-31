package kz.jondeu.materials;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "materials")
@Getter @Setter @NoArgsConstructor
public class Material {
    public enum Status { PLANNED, ORDERED, PARTIALLY_DELIVERED, DELIVERED, CANCELLED }

    @Id private UUID id = UUID.randomUUID();
    private UUID projectId;
    private String name;
    private String unit;
    private BigDecimal plannedQty;
    private BigDecimal unitPrice;
    private BigDecimal actualUnitPrice;
    private BigDecimal receivedQty = BigDecimal.ZERO;
    private String supplier;
    @Enumerated(EnumType.STRING) private Status status = Status.PLANNED;
    private LocalDate deliveryDate;
    private Instant createdAt = Instant.now();
    @Version private Long version;
}
