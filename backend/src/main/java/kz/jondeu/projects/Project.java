package kz.jondeu.projects;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "projects")
@Getter @Setter @NoArgsConstructor
public class Project {
    @Id private UUID id = UUID.randomUUID();
    private UUID ownerId;
    private UUID contractorId;
    private String name;
    private String propertyType;
    private Integer rooms;
    @Column(name = "area_m2") private BigDecimal areaM2;
    private String address;
    private String city = "Almaty";
    private String description;
    private String category;
    @Enumerated(EnumType.STRING) private ProjectStatus status = ProjectStatus.DRAFT;
    private BigDecimal budgetMin;
    private BigDecimal budgetMax;
    private BigDecimal approvedBudget = BigDecimal.ZERO.setScale(2);
    private String currency = "KZT";
    private boolean materialsIncluded = true;
    private String urgency = "NORMAL";
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean archived;
    private boolean demo;
    @Version private Long version;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
