package kz.jondeu.messaging;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "conversation_members")
@IdClass(ConversationMember.Key.class)
@Getter @Setter @NoArgsConstructor
public class ConversationMember {
    @EqualsAndHashCode
    public static class Key implements Serializable {
        public UUID conversationId;
        public UUID userId;
        public Key() {}
        public Key(UUID c, UUID u) { conversationId = c; userId = u; }
    }

    @Id private UUID conversationId;
    @Id private UUID userId;
    private Instant lastReadAt = Instant.EPOCH;

    public ConversationMember(UUID c, UUID u) { conversationId = c; userId = u; lastReadAt = Instant.now(); }
}
