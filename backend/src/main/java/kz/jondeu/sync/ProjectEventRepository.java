package kz.jondeu.sync;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProjectEventRepository extends JpaRepository<ProjectEvent, Long> {
    List<ProjectEvent> findByProjectIdAndSeqGreaterThanOrderBySeqAsc(UUID projectId, long seq, Pageable pageable);
    List<ProjectEvent> findByProjectIdOrderBySeqDesc(UUID projectId, Pageable pageable);

    @Query("select coalesce(max(e.seq), 0) from ProjectEvent e where e.projectId = :projectId")
    long lastSeq(UUID projectId);
}
