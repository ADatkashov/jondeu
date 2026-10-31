package kz.jondeu.notifications;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable p);
    long countByUserIdAndReadFalse(UUID userId);

    @Modifying
    @Query("update Notification n set n.read = true where n.userId = :uid and n.read = false")
    int markAllRead(UUID uid);

    boolean existsByUserIdAndTypeAndProjectIdAndBody(UUID userId, String type, UUID projectId, String body);
}
