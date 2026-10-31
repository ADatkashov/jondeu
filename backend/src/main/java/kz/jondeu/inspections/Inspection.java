package kz.jondeu.inspections;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inspections")
@Getter @Setter @NoArgsConstructor
public class Inspection {
    public enum Result { PASSED, NEEDS_IMPROVEMENT, FAILED, PENDING }

    @Id private UUID id = UUID.randomUUID();
    private UUID projectId;
    private UUID stageId;
    private String title;
    private LocalDate inspectionDate;
    private UUID inspectorId;
    @Enumerated(EnumType.STRING) private Result result = Result.PENDING;
    private String comments;
    private String correctiveActions;
    private Instant createdAt = Instant.now();
}
