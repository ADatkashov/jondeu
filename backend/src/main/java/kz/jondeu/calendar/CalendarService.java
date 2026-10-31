package kz.jondeu.calendar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import kz.jondeu.common.ApiException;
import kz.jondeu.identity.AuthUser;
import kz.jondeu.inspections.InspectionRepository;
import kz.jondeu.materials.MaterialRepository;
import kz.jondeu.projects.ProjectAccess;
import kz.jondeu.projects.ProjectRole.Perm;
import kz.jondeu.stages.StageRepository;
import kz.jondeu.stages.StageStatus;
import kz.jondeu.sync.ProjectEvents;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The calendar is a read model: explicit events + dates derived from stages, material deliveries and inspections,
 * so a date changed on a stage or delivery is instantly reflected without duplicate data entry.
 */
@Service
public class CalendarService {
    public static final ZoneId ZONE = ZoneId.of("Asia/Almaty");

    public record EventInput(String type, String title, Instant startsAt, Instant endsAt) {}
    public record CalendarItem(String id, String type, String title, LocalDate date, Instant startsAt, Instant endsAt, String source, UUID refId, boolean editable, String status) {}

    private final CalendarEventRepository repo;
    private final StageRepository stages;
    private final MaterialRepository materials;
    private final InspectionRepository inspections;
    private final ProjectAccess access;
    private final ProjectEvents events;

    public CalendarService(CalendarEventRepository repo, StageRepository stages, MaterialRepository materials, InspectionRepository inspections, ProjectAccess access, ProjectEvents events) {
        this.repo = repo; this.stages = stages; this.materials = materials; this.inspections = inspections; this.access = access; this.events = events;
    }

    @Transactional(readOnly = true)
    public List<CalendarItem> range(UUID projectId, LocalDate from, LocalDate to, AuthUser u) {
        access.require(projectId, u, Perm.VIEW);
        if (to.isBefore(from) || from.plusDays(400).isBefore(to)) throw ApiException.bad("INVALID_RANGE", "Invalid date range");
        List<CalendarItem> out = new ArrayList<>();
        repo.findByProjectIdAndStartsAtBetweenOrderByStartsAt(projectId, from.atStartOfDay(ZONE).toInstant(), to.plusDays(1).atStartOfDay(ZONE).toInstant())
            .forEach(e -> out.add(new CalendarItem("event:" + e.getId(), e.getType(), e.getTitle(), e.getStartsAt().atZone(ZONE).toLocalDate(), e.getStartsAt(), e.getEndsAt(), "EVENT", e.getId(), true, null)));
        stages.findByProjectIdOrderByPosition(projectId).forEach(s -> {
            if (s.getEndDate() != null && !s.getEndDate().isBefore(from) && !s.getEndDate().isAfter(to)) {
                out.add(new CalendarItem("stage:" + s.getId(), "WORK_DEADLINE", s.getTitle(), s.getEndDate(), null, null, "STAGE", s.getId(), false, s.getStatus().name()));
            }
            if (s.getStartDate() != null && !s.getStartDate().isBefore(from) && !s.getStartDate().isAfter(to)) {
                out.add(new CalendarItem("stage-start:" + s.getId(), "WORK_START", s.getTitle(), s.getStartDate(), null, null, "STAGE", s.getId(), false, s.getStatus().name()));
            }
        });
        materials.findByProjectIdOrderByCreatedAt(projectId).forEach(m -> {
            if (m.getDeliveryDate() != null && !m.getDeliveryDate().isBefore(from) && !m.getDeliveryDate().isAfter(to)) {
                out.add(new CalendarItem("material:" + m.getId(), "MATERIAL_DELIVERY", m.getName(), m.getDeliveryDate(), null, null, "MATERIAL", m.getId(), false, m.getStatus().name()));
            }
        });
        inspections.findByProjectIdOrderByInspectionDateDescCreatedAtDesc(projectId).forEach(i -> {
            if (!i.getInspectionDate().isBefore(from) && !i.getInspectionDate().isAfter(to)) {
                out.add(new CalendarItem("inspection:" + i.getId(), "INSPECTION", i.getTitle(), i.getInspectionDate(), null, null, "INSPECTION", i.getId(), false, i.getResult().name()));
            }
        });
        out.sort(Comparator.comparing(CalendarItem::date).thenComparing(CalendarItem::title));
        return out;
    }

    @Transactional
    public CalendarItem create(UUID projectId, EventInput in, AuthUser u) {
        access.require(projectId, u, Perm.MANAGE_CALENDAR);
        CalendarEvent e = new CalendarEvent();
        e.setProjectId(projectId);
        fill(e, in);
        e.setCreatedBy(u.id());
        repo.save(e);
        events.publish(projectId, "CALENDAR_EVENT_CREATED", "calendar", e.getId(), u.id(), "Событие в календаре: " + e.getTitle());
        return toItem(e);
    }

    @Transactional
    public CalendarItem update(UUID id, EventInput in, AuthUser u) {
        CalendarEvent e = repo.findById(id).orElseThrow(() -> ApiException.notFound("Event"));
        access.require(e.getProjectId(), u, Perm.MANAGE_CALENDAR);
        fill(e, in);
        repo.save(e);
        events.publish(e.getProjectId(), "CALENDAR_EVENT_UPDATED", "calendar", id, u.id(), "Изменено событие: " + e.getTitle());
        return toItem(e);
    }

    @Transactional
    public void delete(UUID id, AuthUser u) {
        CalendarEvent e = repo.findById(id).orElseThrow(() -> ApiException.notFound("Event"));
        access.require(e.getProjectId(), u, Perm.MANAGE_CALENDAR);
        repo.delete(e);
        events.publish(e.getProjectId(), "CALENDAR_EVENT_DELETED", "calendar", id, u.id(), "Удалено событие: " + e.getTitle());
    }

    private void fill(CalendarEvent e, EventInput in) {
        if (in.title() == null || in.title().isBlank()) throw ApiException.bad("VALIDATION_FAILED", "title is required");
        if (in.startsAt() == null) throw ApiException.bad("VALIDATION_FAILED", "startsAt is required");
        if (in.endsAt() != null && in.endsAt().isBefore(in.startsAt())) throw ApiException.bad("INVALID_DATES", "endsAt must not be before startsAt");
        e.setTitle(in.title().trim()); e.setStartsAt(in.startsAt()); e.setEndsAt(in.endsAt());
        e.setType(in.type() == null || in.type().isBlank() ? "CONTRACTOR_VISIT" : in.type());
    }

    private static CalendarItem toItem(CalendarEvent e) {
        return new CalendarItem("event:" + e.getId(), e.getType(), e.getTitle(), e.getStartsAt().atZone(ZONE).toLocalDate(), e.getStartsAt(), e.getEndsAt(), "EVENT", e.getId(), true, null);
    }
}
