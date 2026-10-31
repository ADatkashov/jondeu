package kz.jondeu.masters;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "master_profiles")
@Getter @Setter @NoArgsConstructor
public class MasterProfile {
    @Id private UUID userId;
    private String specialization;
    private String category;
    private String bio;
    private int experienceYears;
    private BigDecimal rating = BigDecimal.ZERO;
    private int reviewsCount;
    private int completedProjects;
    private BigDecimal hourlyRate;
    private String currency = "KZT";
    private boolean verified;
    private boolean available = true;
    private String companyName;
    private String district;
    private Instant createdAt = Instant.now();
}
