package kz.jondeu.stages;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StageCommentRepository extends JpaRepository<StageComment, UUID> {
    List<StageComment> findByStageIdOrderByCreatedAt(UUID stageId);
}
