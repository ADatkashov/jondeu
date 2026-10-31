package kz.jondeu.estimates;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "estimate_items")
@Getter @Setter @NoArgsConstructor
public class EstimateItem {
    public enum Kind { LABOR, MATERIAL, OTHER }

    @Id private UUID id = UUID.randomUUID();
    private UUID estimateId;
    private int position;
    @Enumerated(EnumType.STRING) private Kind kind = Kind.LABOR;
    private String title;
    private String description;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal unitPrice;
    private BigDecimal total;
}
