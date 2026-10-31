package kz.jondeu.materials;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialRepository extends JpaRepository<Material, UUID> {
    List<Material> findByProjectIdOrderByCreatedAt(UUID projectId);
}
