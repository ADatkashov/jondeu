package kz.jondeu.notifications;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notifications")
@Getter @Setter @NoArgsConstructor
public class Notification {
    @Id private UUID id = UUID.randomUUID();
    private UUID userId;
    private UUID projectId;
    private String type;
    private String title;
    private String body;
    private boolean read;
    private Instant createdAt = Instant.now();
}
