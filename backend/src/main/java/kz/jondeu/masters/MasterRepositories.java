package kz.jondeu.masters;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public final class MasterRepositories {
    private MasterRepositories() {}

    public interface MasterProfileRepository extends JpaRepository<MasterProfile, UUID> {
        @Query("""
            select m from MasterProfile m, User u where m.userId = u.id and u.enabled = true
              and (:category is null or m.category = :category)
              and (:city is null or u.city = :city)
              and (:minRating is null or m.rating >= :minRating)
              and (:maxRate is null or m.hourlyRate <= :maxRate)
              and (:minExp is null or m.experienceYears >= :minExp)
              and (:verified is null or m.verified = :verified)
              and (:available is null or m.available = :available)
              and (:q is null or lower(u.fullName) like :q or lower(m.specialization) like :q)""")
        Page<MasterProfile> search(String category, String city, BigDecimal minRating, BigDecimal maxRate, Integer minExp, Boolean verified, Boolean available, String q, Pageable p);
    }

    public interface ReviewRepository extends JpaRepository<Review, UUID> {
        List<Review> findByMasterIdOrderByCreatedAtDesc(UUID masterId);
        boolean existsByProjectIdAndAuthorIdAndMasterId(UUID projectId, UUID authorId, UUID masterId);

        @Query("select coalesce(avg(r.rating), 0) from Review r where r.masterId = :id")
        Double average(UUID id);

        long countByMasterId(UUID masterId);
    }
}
