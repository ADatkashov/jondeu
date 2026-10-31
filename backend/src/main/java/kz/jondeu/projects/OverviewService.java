package kz.jondeu.projects;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import kz.jondeu.estimates.*;
import kz.jondeu.identity.AuthUser;
import kz.jondeu.inspections.Inspection;
import kz.jondeu.inspections.InspectionRepository;
import kz.jondeu.inspections.InspectionService;
import kz.jondeu.materials.Material;
import kz.jondeu.materials.MaterialRepository;
import kz.jondeu.materials.MaterialService;
import kz.jondeu.media.MediaRepository;
import kz.jondeu.payments.FinanceService;
import kz.jondeu.payments.Payment;
import kz.jondeu.payments.PaymentEnums;
import kz.jondeu.payments.PaymentRepository;
import kz.jondeu.projects.ProjectRole.Perm;
import kz.jondeu.stages.*;
import kz.jondeu.sync.ProjectEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Server-side read model consumed identically by the Flutter apps and the web dashboard,
 * so that both always show the same numbers and neither re-implements business logic.
 */
@Service
public class OverviewService {
    public record Finance(String currency, BigDecimal approvedBudget, BigDecimal paid, BigDecimal reserved, BigDecimal remaining, BigDecimal utilizationPercent,
                          BigDecimal materialsPlanned, BigDecimal materialsActual, Map<String, BigDecimal> costBreakdown, BigDecimal pendingRequests) {}
    public record Progress(BigDecimal percent, int stagesTotal, int stagesCompleted, StageService.StageView currentStage) {}
    public record Schedule(LocalDate startDate, LocalDate endDate, Long daysLeft, int overdueStages) {}
    public record Quality(String status, int inspections, Map<String, Long> byResult) {}
    public record Counts(long photos, long pendingEstimates, long openPaymentRequests) {}
    public record Overview(ProjectService.ProjectView project, Finance finance, Progress progress, Schedule schedule, Quality quality, Counts counts, long lastSeq) {}

    private final ProjectAccess access;
    private final ProjectService projectService;
    private final FinanceService financeService;
    private final StageRepository stages;
    private final EstimateRepository estimates;
    private final EstimateItemRepository estimateItems;
    private final MaterialRepository materials;
    private final InspectionRepository inspections;
    private final PaymentRepository payments;
    private final MediaRepository media;
    private final ProjectEventRepository eventRepo;

    public OverviewService(ProjectAccess access, ProjectService projectService, FinanceService financeService, StageRepository stages, EstimateRepository estimates,
                           EstimateItemRepository estimateItems, MaterialRepository materials, InspectionRepository inspections, PaymentRepository payments, MediaRepository media,
                           ProjectEventRepository eventRepo) {
        this.access = access; this.projectService = projectService; this.financeService = financeService; this.stages = stages; this.estimates = estimates;
        this.estimateItems = estimateItems; this.materials = materials; this.inspections = inspections; this.payments = payments; this.media = media; this.eventRepo = eventRepo;
    }

    @Transactional(readOnly = true)
    public Overview overview(UUID projectId, AuthUser u) {
        Project p = access.require(projectId, u, Perm.VIEW);
        List<Stage> st = stages.findByProjectIdOrderByPosition(projectId);
        var pos = financeService.position(projectId, p.getApprovedBudget());
        BigDecimal util = pos.approvedBudget().signum() == 0 ? BigDecimal.ZERO.setScale(1) : pos.paid().multiply(new BigDecimal(100)).divide(pos.approvedBudget(), 1, RoundingMode.HALF_UP);

        List<Material> mats = materials.findByProjectIdOrderByCreatedAt(projectId);
        var ms = MaterialService.summarize(mats);

        Map<String, BigDecimal> breakdown = new LinkedHashMap<>();
        breakdown.put("LABOR", BigDecimal.ZERO.setScale(2)); breakdown.put("MATERIAL", BigDecimal.ZERO.setScale(2)); breakdown.put("OTHER", BigDecimal.ZERO.setScale(2));
        estimates.findFirstByProjectIdAndStatus(projectId, EstimateStatus.APPROVED).ifPresent(e -> {
            estimateItems.findByEstimateIdOrderByPosition(e.getId()).forEach(i -> breakdown.merge(i.getKind().name(), i.getTotal(), BigDecimal::add));
            breakdown.merge("OTHER", e.getAdditionalCosts(), BigDecimal::add);
        });

        BigDecimal pendingReq = payments.sumByStatuses(projectId, List.of(PaymentEnums.Status.REQUESTED));
        Finance fin = new Finance(p.getCurrency(), pos.approvedBudget(), pos.paid(), pos.reserved(), pos.remaining(), util, ms.plannedCost(), ms.actualCost(), breakdown, pendingReq == null ? BigDecimal.ZERO.setScale(2) : pendingReq);

        StageService.StageView current = st.stream().filter(s -> s.getStatus() == StageStatus.IN_PROGRESS || s.getStatus() == StageStatus.UNDER_REVIEW || s.getStatus() == StageStatus.DELAYED)
            .findFirst().or(() -> st.stream().filter(s -> s.getStatus() == StageStatus.NOT_STARTED).findFirst()).map(StageService.StageView::of).orElse(null);
        Progress prog = new Progress(ProgressCalculator.weighted(st), st.size(), (int) st.stream().filter(s -> s.getStatus() == StageStatus.COMPLETED).count(), current);

        LocalDate today = LocalDate.now();
        LocalDate end = st.stream().map(Stage::getEndDate).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(p.getEndDate());
        int overdue = (int) st.stream().filter(s -> s.getEndDate() != null && s.getEndDate().isBefore(today) && s.getStatus() != StageStatus.COMPLETED).count();
        Schedule sch = new Schedule(p.getStartDate(), end, end == null ? null : ChronoUnit.DAYS.between(today, end), overdue);

        List<Inspection> ins = inspections.findByProjectIdOrderByInspectionDateDescCreatedAtDesc(projectId);
        Map<String, Long> by = new LinkedHashMap<>();
        for (Inspection.Result r : Inspection.Result.values()) by.put(r.name(), ins.stream().filter(i -> i.getResult() == r).count());
        Quality q = new Quality(InspectionService.qualityStatus(ins), ins.size(), by);

        Counts counts = new Counts(media.countByProjectIdAndCategoryNot(projectId, "CHAT"),
            estimates.findByProjectIdAndStatusIn(projectId, List.of(EstimateStatus.SUBMITTED)).size(),
            payments.findByProjectIdOrderByCreatedAtDesc(projectId).stream().filter(x -> x.getStatus() == PaymentEnums.Status.REQUESTED).count());
        return new Overview(projectService.view(p, u), fin, prog, sch, q, counts, eventRepo.lastSeq(projectId));
    }
}
