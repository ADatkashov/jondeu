package kz.jondeu.estimates;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;
import kz.jondeu.common.ApiException;
import kz.jondeu.common.Money;
import kz.jondeu.identity.AuthUser;
import kz.jondeu.notifications.Notifier;
import kz.jondeu.payments.FinanceService;
import kz.jondeu.projects.*;
import kz.jondeu.projects.ProjectRole.Perm;
import kz.jondeu.stages.StageService;
import kz.jondeu.sync.ProjectEvents;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EstimateService {
    public record ItemInput(EstimateItem.Kind kind, String title, String description, BigDecimal quantity, String unit, BigDecimal unitPrice) {}
    public record EstimateInput(List<ItemInput> items, BigDecimal additionalCosts, BigDecimal discount, BigDecimal taxRate) {}
    public record ItemView(UUID id, int position, EstimateItem.Kind kind, String title, String description, BigDecimal quantity, String unit, BigDecimal unitPrice, BigDecimal total) {}
    public record EstimateView(UUID id, UUID projectId, String number, int version, UUID contractorId, EstimateStatus status, String currency,
                               BigDecimal subtotal, BigDecimal additionalCosts, BigDecimal discount, BigDecimal taxRate, BigDecimal taxAmount, BigDecimal total,
                               BigDecimal paid, BigDecimal remaining, String reviewComment, Instant approvedAt, Instant createdAt, Instant updatedAt, List<ItemView> items) {}

    private final EstimateRepository estimates;
    private final EstimateItemRepository items;
    private final ProjectAccess access;
    private final ProjectRepository projects;
    private final ProjectEvents events;
    private final Notifier notifier;
    private final StageService stageService;
    private final FinanceService finance;

    public EstimateService(EstimateRepository estimates, EstimateItemRepository items, ProjectAccess access, ProjectRepository projects, ProjectEvents events,
                           Notifier notifier, StageService stageService, FinanceService finance) {
        this.estimates = estimates; this.items = items; this.access = access; this.projects = projects; this.events = events;
        this.notifier = notifier; this.stageService = stageService; this.finance = finance;
    }

    @Transactional(readOnly = true)
    public List<EstimateView> list(UUID projectId, AuthUser u) {
        Project p = access.require(projectId, u, Perm.VIEW);
        List<Estimate> all = estimates.findByProjectIdOrderByNumberAscVersionDesc(projectId);
        Map<UUID, List<EstimateItem>> byEst = items.findByEstimateIdInOrderByPosition(all.stream().map(Estimate::getId).toList())
            .stream().collect(Collectors.groupingBy(EstimateItem::getEstimateId));
        return all.stream().map(e -> view(e, byEst.getOrDefault(e.getId(), List.of()), p)).toList();
    }

    @Transactional(readOnly = true)
    public EstimateView get(UUID id, AuthUser u) {
        Estimate e = load(id);
        Project p = access.require(e.getProjectId(), u, Perm.VIEW);
        return view(e, items.findByEstimateIdOrderByPosition(id), p);
    }

    @Transactional
    public EstimateView create(UUID projectId, EstimateInput in, AuthUser u) {
        Project p = access.require(projectId, u, Perm.EDIT_ESTIMATE);
        if (p.getStatus() == ProjectStatus.COMPLETED || p.getStatus() == ProjectStatus.CANCELLED || p.getStatus() == ProjectStatus.DRAFT) {
            throw ApiException.conflict("PROJECT_STATE", "Estimates cannot be created in project status " + p.getStatus());
        }
        if (!p.getStatus().isActive() && !u.isAdmin()) throw ApiException.conflict("PROJECT_STATE", "A contractor must be selected before creating an estimate");
        Estimate e = new Estimate();
        e.setProjectId(projectId); e.setContractorId(p.getContractorId() != null ? p.getContractorId() : u.id()); e.setCurrency(p.getCurrency());
        e.setNumber("EST-" + Year.now() + "-" + String.format("%03d", estimates.countNumbers(projectId) + 1));
        apply(e, in);
        events.publish(projectId, "ESTIMATE_CREATED", "estimate", e.getId(), u.id(), "Создана смета " + e.getNumber());
        return view(e, items.findByEstimateIdOrderByPosition(e.getId()), p);
    }

    @Transactional
    public EstimateView update(UUID id, EstimateInput in, AuthUser u) {
        Estimate e = load(id);
        Project p = access.require(e.getProjectId(), u, Perm.EDIT_ESTIMATE);
        if (e.getStatus() != EstimateStatus.DRAFT && e.getStatus() != EstimateStatus.CHANGES_REQUESTED && e.getStatus() != EstimateStatus.REJECTED) {
            throw ApiException.conflict("ESTIMATE_IMMUTABLE", "Estimate in status " + e.getStatus() + " cannot be edited; create a new version instead");
        }
        e.setStatus(EstimateStatus.DRAFT);
        apply(e, in);
        events.publish(e.getProjectId(), "ESTIMATE_UPDATED", "estimate", e.getId(), u.id(), "Смета " + e.getNumber() + " v" + e.getVersion() + " изменена");
        return view(e, items.findByEstimateIdOrderByPosition(e.getId()), p);
    }

    @Transactional
    public EstimateView submit(UUID id, AuthUser u) {
        Estimate e = load(id);
        Project p = access.require(e.getProjectId(), u, Perm.EDIT_ESTIMATE);
        if (e.getStatus() != EstimateStatus.DRAFT) throw ApiException.conflict("INVALID_TRANSITION", "Only a draft can be submitted");
        if (items.findByEstimateIdOrderByPosition(id).isEmpty() || e.getTotal().signum() <= 0) throw ApiException.unprocessable("EMPTY_ESTIMATE", "Estimate must have items and a positive total");
        e.setStatus(EstimateStatus.SUBMITTED); e.setReviewComment(null); e.setUpdatedAt(Instant.now());
        estimates.save(e);
        events.publish(e.getProjectId(), "ESTIMATE_SUBMITTED", "estimate", id, u.id(), "Смета " + e.getNumber() + " отправлена на согласование");
        notifier.project(e.getProjectId(), u.id(), "ESTIMATE_SUBMITTED", "Смета на согласовании", e.getNumber() + " — " + e.getTotal() + " " + e.getCurrency());
        return view(e, items.findByEstimateIdOrderByPosition(id), p);
    }

    @Transactional
    public EstimateView approve(UUID id, AuthUser u) {
        Estimate e = load(id);
        Project p = access.requireLocked(e.getProjectId(), u, Perm.APPROVE_ESTIMATE);
        if (e.getStatus() != EstimateStatus.SUBMITTED) throw ApiException.conflict("INVALID_TRANSITION", "Only a submitted estimate can be approved");
        BigDecimal netPaid = finance.netPaid(p.getId());
        if (e.getTotal().compareTo(netPaid) < 0) {
            throw ApiException.unprocessable("BUDGET_BELOW_PAID", "Approved total cannot be lower than the amount already paid (" + netPaid + ")");
        }
        // supersede the previously approved version of the project
        estimates.findByProjectIdAndStatusIn(p.getId(), List.of(EstimateStatus.APPROVED)).forEach(old -> { old.setStatus(EstimateStatus.SUPERSEDED); old.setUpdatedAt(Instant.now()); });
        e.setStatus(EstimateStatus.APPROVED); e.setApprovedAt(Instant.now()); e.setUpdatedAt(Instant.now());
        estimates.save(e);
        p.setApprovedBudget(e.getTotal());
        if (p.getContractorId() == null) p.setContractorId(e.getContractorId());
        if (p.getStatus() == ProjectStatus.CONTRACTOR_SELECTED) p.setStatus(ProjectStatus.IN_PROGRESS);
        p.setUpdatedAt(Instant.now());
        projects.save(p);
        events.publish(p.getId(), "ESTIMATE_APPROVED", "estimate", id, u.id(), "Смета " + e.getNumber() + " v" + e.getVersion() + " утверждена: " + e.getTotal() + " " + e.getCurrency());
        stageService.createDefaults(p, e.getTotal());
        stageService.rescaleBudgets(p.getId(), e.getTotal());
        notifier.project(p.getId(), u.id(), "ESTIMATE_APPROVED", "Смета утверждена", e.getNumber() + " — " + e.getTotal() + " " + e.getCurrency());
        return view(e, items.findByEstimateIdOrderByPosition(id), p);
    }

    @Transactional
    public EstimateView reject(UUID id, String comment, boolean requestChanges, AuthUser u) {
        Estimate e = load(id);
        Project p = access.require(e.getProjectId(), u, Perm.APPROVE_ESTIMATE);
        if (e.getStatus() != EstimateStatus.SUBMITTED) throw ApiException.conflict("INVALID_TRANSITION", "Only a submitted estimate can be reviewed");
        e.setStatus(requestChanges ? EstimateStatus.CHANGES_REQUESTED : EstimateStatus.REJECTED);
        e.setReviewComment(comment); e.setUpdatedAt(Instant.now());
        estimates.save(e);
        String type = requestChanges ? "ESTIMATE_CHANGES_REQUESTED" : "ESTIMATE_REJECTED";
        events.publish(e.getProjectId(), type, "estimate", id, u.id(), (requestChanges ? "Запрошены правки сметы " : "Смета отклонена: ") + e.getNumber());
        notifier.project(e.getProjectId(), u.id(), type, requestChanges ? "Запрошены правки сметы" : "Смета отклонена", comment == null ? e.getNumber() : comment);
        return view(e, items.findByEstimateIdOrderByPosition(id), p);
    }

    /** Approved estimates are never edited in place: a revision is a new DRAFT version with copied items. */
    @Transactional
    public EstimateView revise(UUID id, AuthUser u) {
        Estimate base = load(id);
        Project p = access.require(base.getProjectId(), u, Perm.EDIT_ESTIMATE);
        if (base.getStatus() != EstimateStatus.APPROVED) throw ApiException.conflict("INVALID_TRANSITION", "Only an approved estimate can be revised");
        boolean open = estimates.findByProjectIdAndNumberAndStatusIn(base.getProjectId(), base.getNumber(),
            List.of(EstimateStatus.DRAFT, EstimateStatus.SUBMITTED, EstimateStatus.CHANGES_REQUESTED)).stream().findAny().isPresent();
        if (open) throw ApiException.conflict("REVISION_EXISTS", "An open revision already exists");
        int next = estimates.findByProjectIdAndNumberAndStatusIn(base.getProjectId(), base.getNumber(), List.of(EstimateStatus.values())).stream()
            .mapToInt(Estimate::getVersion).max().orElse(1) + 1;
        Estimate e = new Estimate();
        e.setProjectId(base.getProjectId()); e.setNumber(base.getNumber()); e.setVersion(next); e.setContractorId(base.getContractorId()); e.setCurrency(base.getCurrency());
        List<ItemInput> copy = items.findByEstimateIdOrderByPosition(id).stream()
            .map(i -> new ItemInput(i.getKind(), i.getTitle(), i.getDescription(), i.getQuantity(), i.getUnit(), i.getUnitPrice())).toList();
        apply(e, new EstimateInput(copy, base.getAdditionalCosts(), base.getDiscount(), base.getTaxRate()));
        events.publish(e.getProjectId(), "ESTIMATE_REVISION_CREATED", "estimate", e.getId(), u.id(), "Создана версия v" + next + " сметы " + e.getNumber());
        return view(e, items.findByEstimateIdOrderByPosition(e.getId()), p);
    }

    private void apply(Estimate e, EstimateInput in) {
        if (in.items() == null) throw ApiException.bad("VALIDATION_FAILED", "items are required");
        BigDecimal additional = in.additionalCosts() == null ? BigDecimal.ZERO : in.additionalCosts();
        BigDecimal discount = in.discount() == null ? BigDecimal.ZERO : in.discount();
        BigDecimal tax = in.taxRate() == null ? BigDecimal.ZERO : in.taxRate();
        if (additional.signum() < 0 || discount.signum() < 0) throw ApiException.bad("VALIDATION_FAILED", "additionalCosts and discount must not be negative");
        if (tax.signum() < 0 || tax.compareTo(new BigDecimal("100")) > 0) throw ApiException.bad("VALIDATION_FAILED", "taxRate must be between 0 and 100");
        List<BigDecimal> totals = new ArrayList<>();
        List<EstimateItem> rows = new ArrayList<>();
        int pos = 1;
        for (ItemInput i : in.items()) {
            if (i.title() == null || i.title().isBlank()) throw ApiException.bad("VALIDATION_FAILED", "item title is required");
            if (i.quantity() == null || i.quantity().signum() <= 0) throw ApiException.bad("VALIDATION_FAILED", "item quantity must be positive");
            if (i.unitPrice() == null || i.unitPrice().signum() < 0) throw ApiException.bad("VALIDATION_FAILED", "item unitPrice must not be negative");
            EstimateItem it = new EstimateItem();
            it.setEstimateId(e.getId()); it.setPosition(pos++); it.setKind(i.kind() == null ? EstimateItem.Kind.LABOR : i.kind());
            it.setTitle(i.title().trim()); it.setDescription(i.description()); it.setQuantity(i.quantity());
            it.setUnit(i.unit() == null || i.unit().isBlank() ? "шт" : i.unit());
            it.setUnitPrice(Money.scale(i.unitPrice())); it.setTotal(EstimateCalculator.lineTotal(i.quantity(), it.getUnitPrice()));
            totals.add(it.getTotal());
            rows.add(it);
        }
        var r = EstimateCalculator.calculate(totals, additional, discount, tax);
        e.setAdditionalCosts(Money.scale(additional)); e.setDiscount(Money.scale(discount)); e.setTaxRate(tax.setScale(2, java.math.RoundingMode.HALF_UP));
        e.setSubtotal(r.subtotal()); e.setTaxAmount(r.taxAmount()); e.setTotal(r.total());
        e.setUpdatedAt(Instant.now());
        estimates.save(e);
        items.deleteByEstimateId(e.getId());
        items.flush();
        items.saveAll(rows);
    }

    Estimate load(UUID id) { return estimates.findById(id).orElseThrow(() -> ApiException.notFound("Estimate")); }

    EstimateView view(Estimate e, List<EstimateItem> its, Project p) {
        BigDecimal paid = finance.netPaid(e.getProjectId());
        BigDecimal remaining = e.getStatus() == EstimateStatus.APPROVED ? e.getTotal().subtract(paid).max(BigDecimal.ZERO) : e.getTotal();
        return new EstimateView(e.getId(), e.getProjectId(), e.getNumber(), e.getVersion(), e.getContractorId(), e.getStatus(), e.getCurrency(),
            e.getSubtotal(), e.getAdditionalCosts(), e.getDiscount(), e.getTaxRate(), e.getTaxAmount(), e.getTotal(),
            e.getStatus() == EstimateStatus.APPROVED ? paid : Money.ZERO, Money.scale(remaining), e.getReviewComment(), e.getApprovedAt(), e.getCreatedAt(), e.getUpdatedAt(),
            its.stream().map(i -> new ItemView(i.getId(), i.getPosition(), i.getKind(), i.getTitle(), i.getDescription(), i.getQuantity(), i.getUnit(), i.getUnitPrice(), i.getTotal())).toList());
    }
}
