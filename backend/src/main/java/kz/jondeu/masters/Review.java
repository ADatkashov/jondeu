package kz.jondeu.masters;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "reviews")
@Getter @Setter @NoArgsConstructor
public class Review {
    @Id private UUID id = UUID.randomUUID();
    private UUID projectId;
    private UUID authorId;
    private UUID masterId;
    private int rating;
    private String body;
    private Instant createdAt = Instant.now();
}
