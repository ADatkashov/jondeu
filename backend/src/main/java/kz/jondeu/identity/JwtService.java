package kz.jondeu.identity;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import kz.jondeu.config.AppProperties;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final Duration accessTtl;

    public JwtService(AppProperties props) {
        byte[] secret = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) throw new IllegalStateException("jondeu.jwt.secret must be at least 32 bytes");
        this.key = Keys.hmacShaKeyFor(secret);
        this.accessTtl = Duration.ofMinutes(props.jwt().accessTtlMinutes());
    }

    public String issueAccess(User u) {
        Instant now = Instant.now();
        return Jwts.builder().subject(u.getId().toString())
            .claim("role", u.getRole().name()).claim("name", u.getFullName())
            .issuedAt(Date.from(now)).expiration(Date.from(now.plus(accessTtl)))
            .signWith(key).compact();
    }

    public Optional<AuthUser> parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new AuthUser(UUID.fromString(c.getSubject()), Role.valueOf(c.get("role", String.class)), c.get("name", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long accessTtlSeconds() { return accessTtl.toSeconds(); }
}
