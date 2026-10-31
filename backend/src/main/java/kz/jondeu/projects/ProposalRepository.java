package kz.jondeu.projects;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProposalRepository extends JpaRepository<Proposal, UUID> {
    List<Proposal> findByProjectIdOrderByPriceAsc(UUID projectId);
    Optional<Proposal> findByProjectIdAndMasterId(UUID projectId, UUID masterId);
    List<Proposal> findByMasterIdOrderByCreatedAtDesc(UUID masterId);
    long countByProjectId(UUID projectId);
}
