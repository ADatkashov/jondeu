package kz.jondeu.messaging;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "messages")
@Getter @Setter @NoArgsConstructor
public class Message {
    @Id private UUID id = UUID.randomUUID();
    private UUID conversationId;
    private UUID senderId;
    private String body;
    private UUID mediaId;
    private Instant createdAt = Instant.now();
}
