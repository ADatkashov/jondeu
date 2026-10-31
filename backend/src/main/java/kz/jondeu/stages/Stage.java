package kz.jondeu.stages;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stages")
@Getter @Setter @NoArgsConstructor
public class Stage {
    @Id private UUID id = UUID.randomUUID();
    private UUID projectId;
    private int position;
    private String code;
    private String title;
    private String description;
    private BigDecimal weight = BigDecimal.ONE;
    private BigDecimal budget = BigDecimal.ZERO.setScale(2);
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate actualEndDate;
    @Enumerated(EnumType.STRING) private StageStatus status = StageStatus.NOT_STARTED;
    private int progress;
    private UUID dependsOn;
    private UUID assigneeId;
    @Version private Long version;
}
