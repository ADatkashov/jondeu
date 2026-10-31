package kz.jondeu.estimates;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstimateItemRepository extends JpaRepository<EstimateItem, UUID> {
    List<EstimateItem> findByEstimateIdOrderByPosition(UUID estimateId);
    List<EstimateItem> findByEstimateIdInOrderByPosition(Collection<UUID> ids);
    void deleteByEstimateId(UUID estimateId);
}
