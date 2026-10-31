package kz.jondeu.identity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class User {
    @Id private UUID id = UUID.randomUUID();
    private String phone;
    private String email;
    private String passwordHash;
    private String fullName;
    @Enumerated(EnumType.STRING) private Role role;
    private String city = "Almaty";
    private String language = "ru";
    private String avatarUrl;
    private boolean enabled = true;
    private boolean demo;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
