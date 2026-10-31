package kz.jondeu.projects;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "proposals")
@Getter @Setter @NoArgsConstructor
public class Proposal {
    public enum Status { PENDING, ACCEPTED, REJECTED }

    @Id private UUID id = UUID.randomUUID();
    private UUID projectId;
    private UUID masterId;
    private BigDecimal price;
    private String currency = "KZT";
    private int durationDays;
    private String message;
    @Enumerated(EnumType.STRING) private Status status = Status.PENDING;
    private Instant createdAt = Instant.now();
}
