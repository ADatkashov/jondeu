package kz.jondeu.calendar;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, UUID> {
    List<CalendarEvent> findByProjectIdAndStartsAtBetweenOrderByStartsAt(UUID projectId, Instant from, Instant to);
}
