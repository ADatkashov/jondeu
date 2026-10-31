package kz.jondeu.stages;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stage_comments")
@Getter @Setter @NoArgsConstructor
public class StageComment {
    @Id private UUID id = UUID.randomUUID();
    private UUID stageId;
    private UUID authorId;
    private String body;
    private Instant createdAt = Instant.now();
}
