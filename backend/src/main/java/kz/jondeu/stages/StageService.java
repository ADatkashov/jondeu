package kz.jondeu.stages;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kz.jondeu.common.ApiException;
import kz.jondeu.common.Money;
import kz.jondeu.identity.AuthUser;
import kz.jondeu.notifications.Notifier;
import kz.jondeu.projects.Project;
import kz.jondeu.projects.ProjectAccess;
import kz.jondeu.projects.ProjectRepository;
import kz.jondeu.projects.ProjectRole.Perm;
import kz.jondeu.projects.ProjectStatus;
import kz.jondeu.sync.ProjectEvents;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StageService {
    /** code, russian title, weight (sums to 100), duration in days. */
    private static final Object[][] TEMPLATE = {
        {"PREPARATION", "Подготовка проекта", 3, 3}, {"DEMOLITION", "Демонтажные работы", 7, 5},
        {"ROUGH_CONSTRUCTION", "Черновые работы", 12, 10}, {"ELECTRICAL_PLUMBING", "Электрика и сантехника", 15, 10},
        {"PLASTERING", "Штукатурка и выравнивание", 12, 10}, {"FINISHING", "Отделочные работы", 15, 14},
        {"FLOORING_TILING", "Полы и плитка", 12, 10}, {"EQUIPMENT", "Установка оборудования", 8, 5},
        {"CLEANING", "Финальная уборка", 3, 2}, {"INSPECTION", "Финальная приёмка", 5, 2}, {"HANDOVER", "Сдача объекта", 8, 1}
    };

    public record StageView(UUID id, UUID projectId, int position, String code, String title, String description, BigDecimal weight, BigDecimal budget,
                            LocalDate startDate, LocalDate endDate, LocalDate actualEndDate, StageStatus status, int progress,
                            UUID dependsOn, UUID assigneeId, Long version) {
        public static StageView of(Stage s) {
            return new StageView(s.getId(), s.getProjectId(), s.getPosition(), s.getCode(), s.getTitle(), s.getDescription(), s.getWeight(), Money.nz(s.getBudget()),
                s.getStartDate(), s.getEndDate(), s.getActualEndDate(), s.getStatus(), s.getProgress(), s.getDependsOn(), s.getAssigneeId(), s.getVersion());
        }
    }

    public record StageUpdate(String title, String description, BigDecimal weight, BigDecimal budget, LocalDate startDate, LocalDate endDate,
                              Integer progress, StageStatus status, UUID dependsOn, UUID assigneeId, Boolean clearDependency) {}
    public record StageCreate(String title, String description, BigDecimal weight, BigDecimal budget, LocalDate startDate, LocalDate endDate, UUID dependsOn) {}

    private final StageRepository stages;
    private final StageCommentRepository comments;
    private final ProjectAccess access;
    private final ProjectRepository projects;
    private final ProjectEvents events;
    private final Notifier notifier;

    public StageService(StageRepository stages, StageCommentRepository comments, ProjectAccess access, ProjectRepository projects, ProjectEvents events, Notifier notifier) {
        this.stages = stages; this.comments = comments; this.access = access; this.projects = projects; this.events = events; this.notifier = notifier;
    }

    @Transactional(readOnly = true)
    public List<StageView> list(UUID projectId, AuthUser u) {
        access.require(projectId, u, Perm.VIEW);
        return stages.findByProjectIdOrderByPosition(projectId).stream().map(StageView::of).toList();
    }

    /** Creates the default 11-stage plan with budgets distributed by weight (remainder goes to the last stage so the sum is exact). */
    @Transactional
    public void createDefaults(Project p, BigDecimal total) {
        if (stages.existsByProjectId(p.getId())) return;
        LocalDate cursor = p.getStartDate() != null ? p.getStartDate() : LocalDate.now();
        List<Stage> created = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        Stage prev = null;
        for (int i = 0; i < TEMPLATE.length; i++) {
            Object[] t = TEMPLATE[i];
            Stage s = new Stage();
            s.setProjectId(p.getId()); s.setPosition(i + 1); s.setCode((String) t[0]); s.setTitle((String) t[1]);
            s.setWeight(new BigDecimal((int) t[2]));
            BigDecimal b = i == TEMPLATE.length - 1 ? total.subtract(allocated)
                : total.multiply(s.getWeight()).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
            allocated = allocated.add(b);
            s.setBudget(Money.scale(b));
            s.setStartDate(cursor); cursor = cursor.plusDays((int) t[3]); s.setEndDate(cursor.minusDays(1));
            if (prev != null) s.setDependsOn(prev.getId());
            created.add(stages.save(s));
            prev = s;
        }
        p.setEndDate(cursor.minusDays(1));
        if (p.getStartDate() == null) p.setStartDate(created.get(0).getStartDate());
        events.publish(p.getId(), "STAGES_CREATED", "stage", null, null, "Этапы ремонта созданы");
    }

    /** Re-scales stage budgets proportionally when the approved estimate total changes. */
    @Transactional
    public void rescaleBudgets(UUID projectId, BigDecimal newTotal) {
        List<Stage> list = stages.findByProjectIdOrderByPosition(projectId);
        if (list.isEmpty()) return;
        BigDecimal weightSum = list.stream().map(Stage::getWeight).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < list.size(); i++) {
            Stage s = list.get(i);
            BigDecimal b = i == list.size() - 1 ? newTotal.subtract(allocated) : newTotal.multiply(s.getWeight()).divide(weightSum, 2, RoundingMode.HALF_UP);
            s.setBudget(Money.scale(b));
            allocated = allocated.add(b);
        }
        stages.saveAll(list);
    }

    @Transactional
    public StageView add(UUID projectId, StageCreate c, AuthUser u) {
        Project p = access.require(projectId, u, Perm.UPDATE_STAGE);
        if (c.title() == null || c.title().isBlank()) throw ApiException.bad("VALIDATION_FAILED", "title is required");
        List<Stage> existing = stages.findByProjectIdOrderByPosition(projectId);
        Stage s = new Stage();
        s.setProjectId(projectId); s.setPosition(existing.size() + 1); s.setTitle(c.title().trim()); s.setDescription(c.description());
        if (c.weight() != null) s.setWeight(positive(c.weight(), "weight"));
        if (c.budget() != null) s.setBudget(nonNegative(c.budget()));
        s.setStartDate(c.startDate()); s.setEndDate(c.endDate());
        checkDates(s);
        if (c.dependsOn() != null) { checkDependency(s, c.dependsOn(), existing); }
        stages.save(s);
        events.publish(p.getId(), "STAGE_CREATED", "stage", s.getId(), u.id(), "Добавлен этап: " + s.getTitle());
        return StageView.of(s);
    }

    @Transactional
    public StageView update(UUID stageId, StageUpdate r, AuthUser u) {
        Stage s = stages.findById(stageId).orElseThrow(() -> ApiException.notFound("Stage"));
        Project p = access.require(s.getProjectId(), u, Perm.VIEW);
        var role = access.roleOrThrow(p, u);
        StageStatus before = s.getStatus();

        boolean touchesPlan = r.title() != null || r.description() != null || r.weight() != null || r.budget() != null
            || r.startDate() != null || r.endDate() != null || r.dependsOn() != null || r.assigneeId() != null || Boolean.TRUE.equals(r.clearDependency());
        boolean touchesProgress = r.progress() != null || r.status() != null;
        if (touchesPlan && !role.can(Perm.UPDATE_STAGE)) throw ApiException.forbidden("Missing permission UPDATE_STAGE");
        if (touchesProgress && !(role.can(Perm.UPDATE_STAGE) || role.can(Perm.APPROVE_STAGE))) throw ApiException.forbidden("Missing permission UPDATE_STAGE");

        if (r.title() != null && !r.title().isBlank()) s.setTitle(r.title().trim());
        if (r.description() != null) s.setDescription(r.description());
        if (r.weight() != null) s.setWeight(positive(r.weight(), "weight"));
        if (r.budget() != null) s.setBudget(nonNegative(r.budget()));
        if (r.startDate() != null) s.setStartDate(r.startDate());
        if (r.endDate() != null) s.setEndDate(r.endDate());
        if (r.assigneeId() != null) s.setAssigneeId(r.assigneeId());
        checkDates(s);
        if (Boolean.TRUE.equals(r.clearDependency())) s.setDependsOn(null);
        else if (r.dependsOn() != null) checkDependency(s, r.dependsOn(), stages.findByProjectIdOrderByPosition(s.getProjectId()));

        if (r.progress() != null) {
            if (r.progress() < 0 || r.progress() > 100) throw ApiException.bad("VALIDATION_FAILED", "progress must be 0..100");
            if (s.getStatus() == StageStatus.COMPLETED && !role.can(Perm.APPROVE_STAGE)) throw ApiException.conflict("STAGE_COMPLETED", "Completed stage cannot be changed");
            s.setProgress(r.progress());
            if (r.progress() > 0 && (s.getStatus() == StageStatus.NOT_STARTED || s.getStatus() == StageStatus.DELAYED)) requireStartable(s);
            if (r.progress() > 0 && s.getStatus() == StageStatus.NOT_STARTED) s.setStatus(StageStatus.IN_PROGRESS);
            // Contractor reaching 100% never completes a stage on their own: it goes to customer/inspector review.
            if (r.progress() == 100 && s.getStatus() != StageStatus.COMPLETED && r.status() == null) s.setStatus(StageStatus.UNDER_REVIEW);
        }
        if (r.status() != null && r.status() != s.getStatus()) applyStatus(s, r.status(), role.can(Perm.APPROVE_STAGE), role.can(Perm.UPDATE_STAGE));

        stages.save(s);
        stages.flush();
        Project pr = projects.findById(s.getProjectId()).orElseThrow();
        if (pr.getStatus() == ProjectStatus.CONTRACTOR_SELECTED && s.getStatus() == StageStatus.IN_PROGRESS) pr.setStatus(ProjectStatus.IN_PROGRESS);
        String type = s.getStatus() == before ? "STAGE_UPDATED" : switch (s.getStatus()) {
            case COMPLETED -> "STAGE_COMPLETED"; case UNDER_REVIEW -> "STAGE_REVIEW_REQUESTED"; case DELAYED -> "STAGE_DELAYED";
            default -> "STAGE_UPDATED"; };
        events.publish(s.getProjectId(), type, "stage", s.getId(), u.id(), switch (type) {
            case "STAGE_COMPLETED" -> "Этап завершён: " + s.getTitle();
            case "STAGE_REVIEW_REQUESTED" -> "Этап ожидает приёмки: " + s.getTitle();
            default -> "Обновлён этап: " + s.getTitle() + " (" + s.getProgress() + "%)"; });
        if (type.equals("STAGE_COMPLETED")) notifier.project(s.getProjectId(), u.id(), "STAGE_COMPLETED", "Этап завершён", s.getTitle());
        if (type.equals("STAGE_REVIEW_REQUESTED")) notifier.project(s.getProjectId(), u.id(), "STAGE_REVIEW_REQUESTED", "Этап ожидает приёмки", s.getTitle());
        return StageView.of(s);
    }

    private void applyStatus(Stage s, StageStatus target, boolean canApprove, boolean canUpdate) {
        StageStatus cur = s.getStatus();
        switch (target) {
            case COMPLETED -> {
                if (!canApprove) throw ApiException.forbidden("Only the customer or an inspector can accept a stage");
                if (cur != StageStatus.UNDER_REVIEW && cur != StageStatus.IN_PROGRESS) throw ApiException.conflict("INVALID_TRANSITION", "Stage must be submitted for review first");
                s.setStatus(StageStatus.COMPLETED); s.setProgress(100); s.setActualEndDate(LocalDate.now());
            }
            case UNDER_REVIEW -> {
                if (!canUpdate) throw ApiException.forbidden("Missing permission UPDATE_STAGE");
                if (cur != StageStatus.IN_PROGRESS && cur != StageStatus.DELAYED) throw ApiException.conflict("INVALID_TRANSITION", "Stage must be in progress");
                s.setStatus(StageStatus.UNDER_REVIEW); s.setProgress(100);
            }
            case IN_PROGRESS -> {
                // From UNDER_REVIEW this is a rejection by the reviewer (needs rework).
                if (cur == StageStatus.UNDER_REVIEW && !canApprove) throw ApiException.forbidden("Only the customer or an inspector can return a stage to work");
                if (cur == StageStatus.COMPLETED && !canApprove) throw ApiException.conflict("STAGE_COMPLETED", "Completed stage cannot be changed");
                requireStartable(s);
                s.setStatus(StageStatus.IN_PROGRESS); s.setActualEndDate(null);
                if (s.getProgress() == 100) s.setProgress(90);
            }
            case NOT_STARTED, BLOCKED, DELAYED -> {
                if (!canUpdate) throw ApiException.forbidden("Missing permission UPDATE_STAGE");
                if (cur == StageStatus.COMPLETED) throw ApiException.conflict("STAGE_COMPLETED", "Completed stage cannot be changed");
                s.setStatus(target);
            }
        }
    }

    private void requireStartable(Stage s) {
        if (s.getDependsOn() == null) return;
        Stage dep = stages.findById(s.getDependsOn()).orElse(null);
        if (dep != null && dep.getStatus() != StageStatus.COMPLETED) {
            throw ApiException.conflict("STAGE_BLOCKED", "Stage depends on '" + dep.getTitle() + "' which is not completed yet");
        }
    }

    private void checkDependency(Stage s, UUID depId, List<Stage> all) {
        if (depId.equals(s.getId())) throw ApiException.bad("INVALID_DEPENDENCY", "Stage cannot depend on itself");
        Stage dep = all.stream().filter(x -> x.getId().equals(depId)).findFirst()
            .orElseThrow(() -> ApiException.bad("INVALID_DEPENDENCY", "Dependency must be a stage of the same project"));
        // cycle detection
        UUID cur = dep.getDependsOn();
        int guard = 0;
        while (cur != null && guard++ < 100) {
            if (cur.equals(s.getId())) throw ApiException.bad("INVALID_DEPENDENCY", "Dependency cycle detected");
            UUID next = cur;
            cur = all.stream().filter(x -> x.getId().equals(next)).map(Stage::getDependsOn).findFirst().orElse(null);
        }
        s.setDependsOn(depId);
    }

    private static void checkDates(Stage s) {
        if (s.getStartDate() != null && s.getEndDate() != null && s.getEndDate().isBefore(s.getStartDate())) {
            throw ApiException.bad("INVALID_DATES", "End date must not be before start date");
        }
    }

    private static BigDecimal positive(BigDecimal v, String f) {
        if (v.signum() <= 0) throw ApiException.bad("VALIDATION_FAILED", f + " must be positive");
        return v;
    }

    private static BigDecimal nonNegative(BigDecimal v) {
        if (v.signum() < 0) throw ApiException.bad("VALIDATION_FAILED", "budget must not be negative");
        return Money.scale(v);
    }

    // ---- comments
    public record CommentView(UUID id, UUID stageId, UUID authorId, String body, java.time.Instant createdAt) {}

    @Transactional
    public CommentView comment(UUID stageId, String body, AuthUser u) {
        Stage s = stages.findById(stageId).orElseThrow(() -> ApiException.notFound("Stage"));
        access.require(s.getProjectId(), u, Perm.CHAT);
        if (body == null || body.isBlank()) throw ApiException.bad("VALIDATION_FAILED", "body is required");
        StageComment c = new StageComment();
        c.setStageId(stageId); c.setAuthorId(u.id()); c.setBody(body.trim());
        comments.save(c);
        events.publish(s.getProjectId(), "STAGE_COMMENTED", "stage", stageId, u.id(), "Комментарий к этапу: " + s.getTitle());
        return new CommentView(c.getId(), stageId, u.id(), c.getBody(), c.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<CommentView> comments(UUID stageId, AuthUser u) {
        Stage s = stages.findById(stageId).orElseThrow(() -> ApiException.notFound("Stage"));
        access.require(s.getProjectId(), u, Perm.VIEW);
        return comments.findByStageIdOrderByCreatedAt(stageId).stream().map(c -> new CommentView(c.getId(), c.getStageId(), c.getAuthorId(), c.getBody(), c.getCreatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal progress(UUID projectId) { return ProgressCalculator.weighted(stages.findByProjectIdOrderByPosition(projectId)); }
}
