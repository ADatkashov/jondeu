package kz.jondeu.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String hash);

    @Modifying
    @Query("update RefreshToken t set t.revoked = true where t.familyId = :family")
    void revokeFamily(UUID family);

    @Modifying
    @Query("update RefreshToken t set t.revoked = true where t.userId = :user")
    void revokeAllForUser(UUID user);
}
