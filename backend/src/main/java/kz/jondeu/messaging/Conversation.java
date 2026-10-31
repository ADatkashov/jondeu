package kz.jondeu.messaging;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "conversations")
@Getter @Setter @NoArgsConstructor
public class Conversation {
    @Id private UUID id = UUID.randomUUID();
    private UUID projectId;
    private String title;
    private Instant createdAt = Instant.now();
}
