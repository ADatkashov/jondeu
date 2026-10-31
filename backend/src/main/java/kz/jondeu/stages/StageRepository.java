package kz.jondeu.stages;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StageRepository extends JpaRepository<Stage, UUID> {
    List<Stage> findByProjectIdOrderByPosition(UUID projectId);
    boolean existsByProjectId(UUID projectId);
    List<Stage> findByEndDateBeforeAndStatusIn(LocalDate date, Collection<StageStatus> statuses);
}
