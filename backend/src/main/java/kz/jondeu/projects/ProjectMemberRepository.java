package kz.jondeu.projects;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMember.Key> {
    Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId);
    List<ProjectMember> findByProjectId(UUID projectId);
}
