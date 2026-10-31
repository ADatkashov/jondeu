package kz.jondeu.calendar;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "calendar_events")
@Getter @Setter @NoArgsConstructor
public class CalendarEvent {
    @Id private UUID id = UUID.randomUUID();
    private UUID projectId;
    private String type;
    private String title;
    private Instant startsAt;
    private Instant endsAt;
    private UUID createdBy;
}
