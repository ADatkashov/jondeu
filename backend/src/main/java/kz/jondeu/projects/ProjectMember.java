package kz.jondeu.projects;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "project_members")
@IdClass(ProjectMember.Key.class)
@Getter @Setter @NoArgsConstructor
public class ProjectMember {
    @EqualsAndHashCode
    public static class Key implements Serializable {
        public UUID projectId;
        public UUID userId;
        public Key() {}
        public Key(UUID p, UUID u) { projectId = p; userId = u; }
    }

    @Id private UUID projectId;
    @Id private UUID userId;
    @Enumerated(EnumType.STRING) private ProjectRole memberRole;
    private Instant createdAt = Instant.now();

    public ProjectMember(UUID projectId, UUID userId, ProjectRole role) {
        this.projectId = projectId; this.userId = userId; this.memberRole = role;
    }
}
