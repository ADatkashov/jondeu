package kz.jondeu.sync;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "project_events")
@Getter @Setter @NoArgsConstructor
public class ProjectEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long seq;
    private UUID projectId;
    private String type;
    private String entityType;
    private UUID entityId;
    private UUID actorId;
    private String message;
    private Instant createdAt = Instant.now();
}
