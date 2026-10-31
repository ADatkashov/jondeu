package kz.jondeu.admin;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "audit_log")
@Getter @Setter @NoArgsConstructor
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private UUID actorId;
    private String action;
    private String entityType;
    private UUID entityId;
    private String detail;
    private Instant createdAt = Instant.now();
}
