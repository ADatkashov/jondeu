package kz.jondeu.media;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MediaRepository extends JpaRepository<Media, UUID> {
    @Query("""
        select m from Media m where m.projectId = :pid and m.category <> 'CHAT'
          and (:stage is null or m.stageId = :stage) and (:category is null or m.category = :category)
          and (:uploader is null or m.uploaderId = :uploader) and m.createdAt >= :from and m.createdAt < :to
        order by m.createdAt desc""")
    List<Media> search(UUID pid, UUID stage, String category, UUID uploader, Instant from, Instant to);

    long countByProjectIdAndCategoryNot(UUID projectId, String category);
}
