package kz.jondeu.payments;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
    Optional<Payment> findByIdempotencyKey(String key);
    Optional<Payment> findByProviderAndProviderRef(String provider, String providerRef);
    Page<Payment> findAllByOrderByCreatedAtDesc(Pageable p);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.projectId = :pid and p.status in :statuses")
    BigDecimal sumByStatuses(UUID pid, Collection<PaymentEnums.Status> statuses);
}
