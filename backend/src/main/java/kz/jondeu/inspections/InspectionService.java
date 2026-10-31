package kz.jondeu.inspections;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kz.jondeu.common.ApiException;
import kz.jondeu.identity.AuthUser;
import kz.jondeu.inspections.Inspection.Result;
import kz.jondeu.notifications.Notifier;
import kz.jondeu.projects.ProjectAccess;
import kz.jondeu.projects.ProjectRole.Perm;
import kz.jondeu.sync.ProjectEvents;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionService {
    public record InspectionInput(String title, UUID stageId, LocalDate inspectionDate, Result result, String comments, String correctiveActions) {}
    public record InspectionView(UUID id, UUID projectId, UUID stageId, String title, LocalDate inspectionDate, UUID inspectorId, Result result, String comments, String correctiveActions) {
        static InspectionView of(Inspection i) {
            return new InspectionView(i.getId(), i.getProjectId(), i.getStageId(), i.getTitle(), i.getInspectionDate(), i.getInspectorId(), i.getResult(), i.getComments(), i.getCorrectiveActions());
        }
    }

    private final InspectionRepository repo;
    private final ProjectAccess access;
    private final ProjectEvents events;
    private final Notifier notifier;

    public InspectionService(InspectionRepository repo, ProjectAccess access, ProjectEvents events, Notifier notifier) {
        this.repo = repo; this.access = access; this.events = events; this.notifier = notifier;
    }

    /** Overall quality status: worst unresolved result among the latest inspection per title. */
    public static String qualityStatus(List<Inspection> list) {
        if (list.isEmpty()) return "PENDING";
        if (list.stream().anyMatch(i -> i.getResult() == Result.FAILED)) return "FAILED";
        if (list.stream().anyMatch(i -> i.getResult() == Result.NEEDS_IMPROVEMENT)) return "NEEDS_IMPROVEMENT";
        if (list.stream().anyMatch(i -> i.getResult() == Result.PENDING)) return "PENDING";
        return "PASSED";
    }

    @Transactional(readOnly = true)
    public List<InspectionView> list(UUID projectId, AuthUser u) {
        access.require(projectId, u, Perm.VIEW);
        return repo.findByProjectIdOrderByInspectionDateDescCreatedAtDesc(projectId).stream().map(InspectionView::of).toList();
    }

    @Transactional
    public InspectionView create(UUID projectId, InspectionInput in, AuthUser u) {
        access.require(projectId, u, Perm.INSPECT);
        if (in.title() == null || in.title().isBlank()) throw ApiException.bad("VALIDATION_FAILED", "title is required");
        Inspection i = new Inspection();
        i.setProjectId(projectId); i.setStageId(in.stageId()); i.setTitle(in.title().trim());
        i.setInspectionDate(in.inspectionDate() == null ? LocalDate.now() : in.inspectionDate());
        i.setInspectorId(u.id()); i.setResult(in.result() == null ? Result.PENDING : in.result());
        i.setComments(in.comments()); i.setCorrectiveActions(in.correctiveActions());
        repo.save(i);
        events.publish(projectId, "INSPECTION_CREATED", "inspection", i.getId(), u.id(), "Инспекция: " + i.getTitle() + " — " + i.getResult());
        notifier.project(projectId, u.id(), "INSPECTION_SCHEDULED", "Инспекция", i.getTitle() + " — " + i.getInspectionDate());
        return InspectionView.of(i);
    }

    @Transactional
    public InspectionView update(UUID id, InspectionInput in, AuthUser u) {
        Inspection i = repo.findById(id).orElseThrow(() -> ApiException.notFound("Inspection"));
        access.require(i.getProjectId(), u, Perm.INSPECT);
        if (in.title() != null && !in.title().isBlank()) i.setTitle(in.title().trim());
        if (in.inspectionDate() != null) i.setInspectionDate(in.inspectionDate());
        if (in.comments() != null) i.setComments(in.comments());
        if (in.correctiveActions() != null) i.setCorrectiveActions(in.correctiveActions());
        if (in.result() != null) i.setResult(in.result());
        i.setInspectorId(u.id());
        repo.save(i);
        events.publish(i.getProjectId(), "INSPECTION_UPDATED", "inspection", id, u.id(), "Инспекция «" + i.getTitle() + "»: " + i.getResult());
        if (i.getResult() == Result.FAILED || i.getResult() == Result.NEEDS_IMPROVEMENT) {
            notifier.project(i.getProjectId(), u.id(), "INSPECTION_ISSUE", "Замечания по качеству", i.getTitle());
        }
        return InspectionView.of(i);
    }
}
