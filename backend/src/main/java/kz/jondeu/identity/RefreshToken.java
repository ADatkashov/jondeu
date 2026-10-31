package kz.jondeu.identity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "refresh_tokens")
@Getter @Setter @NoArgsConstructor
public class RefreshToken {
    @Id private UUID id = UUID.randomUUID();
    private UUID userId;
    private String tokenHash;
    private UUID familyId;
    private Instant expiresAt;
    private boolean revoked;
    private Instant createdAt = Instant.now();
}
