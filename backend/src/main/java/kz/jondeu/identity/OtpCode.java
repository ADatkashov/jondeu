package kz.jondeu.identity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "otp_codes")
@Getter @Setter @NoArgsConstructor
public class OtpCode {
    @Id private String phone;
    private String codeHash;
    private Instant expiresAt;
    private int attempts;
}
