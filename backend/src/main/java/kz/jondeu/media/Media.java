package kz.jondeu.media;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "media")
@Getter @Setter @NoArgsConstructor
public class Media {
    @Id private UUID id = UUID.randomUUID();
    private UUID projectId;
    private UUID stageId;
    private UUID uploaderId;
    private String category;
    private String filename;
    private String contentType;
    private long sizeBytes;
    private String storageKey;
    private String caption;
    private Instant createdAt = Instant.now();
}
