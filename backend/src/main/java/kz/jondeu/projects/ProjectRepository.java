package kz.jondeu.projects;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    @Query("select p from Project p where p.archived = :archived and (p.ownerId = :uid or p.id in (select m.projectId from ProjectMember m where m.userId = :uid))")
    Page<Project> findAccessible(UUID uid, boolean archived, Pageable pageable);

    Page<Project> findByStatusIn(Collection<ProjectStatus> statuses, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id")
    Optional<Project> lockById(UUID id);

    long countByStatus(ProjectStatus status);
}
