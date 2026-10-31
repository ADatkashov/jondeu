package kz.jondeu.estimates;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EstimateRepository extends JpaRepository<Estimate, UUID> {
    List<Estimate> findByProjectIdOrderByNumberAscVersionDesc(UUID projectId);
    Optional<Estimate> findFirstByProjectIdAndStatus(UUID projectId, EstimateStatus status);
    List<Estimate> findByProjectIdAndNumberAndStatusIn(UUID projectId, String number, Collection<EstimateStatus> statuses);
    List<Estimate> findByProjectIdAndStatusIn(UUID projectId, Collection<EstimateStatus> statuses);

    @Query("select count(distinct e.number) from Estimate e where e.projectId = :projectId")
    long countNumbers(UUID projectId);
}
