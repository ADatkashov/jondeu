package kz.jondeu.materials;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kz.jondeu.common.ApiException;
import kz.jondeu.common.Money;
import kz.jondeu.identity.AuthUser;
import kz.jondeu.materials.Material.Status;
import kz.jondeu.notifications.Notifier;
import kz.jondeu.projects.ProjectAccess;
import kz.jondeu.projects.ProjectRole.Perm;
import kz.jondeu.sync.ProjectEvents;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaterialService {
    public record MaterialInput(String name, String unit, BigDecimal plannedQty, BigDecimal unitPrice, String supplier, LocalDate deliveryDate) {}
    public record ReceiveInput(BigDecimal quantity, BigDecimal actualUnitPrice, LocalDate deliveryDate) {}
    public record MaterialView(UUID id, UUID projectId, String name, String unit, BigDecimal plannedQty, BigDecimal unitPrice, BigDecimal plannedCost,
                               BigDecimal actualUnitPrice, BigDecimal actualCost, BigDecimal receivedQty, String supplier, Status status, LocalDate deliveryDate, Long version) {}
    public record Summary(BigDecimal plannedCost, BigDecimal actualCost, BigDecimal variance, int total, int delivered) {}
    public record MaterialsResponse(List<MaterialView> items, Summary summary) {}

    private final MaterialRepository repo;
    private final ProjectAccess access;
    private final ProjectEvents events;
    private final Notifier notifier;

    public MaterialService(MaterialRepository repo, ProjectAccess access, ProjectEvents events, Notifier notifier) {
        this.repo = repo; this.access = access; this.events = events; this.notifier = notifier;
    }

    static MaterialView view(Material m) {
        BigDecimal planned = Money.mul(m.getPlannedQty(), m.getUnitPrice());
        BigDecimal actual = Money.mul(m.getReceivedQty(), m.getActualUnitPrice() != null ? m.getActualUnitPrice() : m.getUnitPrice());
        return new MaterialView(m.getId(), m.getProjectId(), m.getName(), m.getUnit(), m.getPlannedQty(), Money.scale(m.getUnitPrice()), planned,
            m.getActualUnitPrice() == null ? null : Money.scale(m.getActualUnitPrice()), actual, m.getReceivedQty(), m.getSupplier(), m.getStatus(), m.getDeliveryDate(), m.getVersion());
    }

    public static Summary summarize(List<Material> list) {
        BigDecimal planned = BigDecimal.ZERO, actual = BigDecimal.ZERO;
        int delivered = 0;
        for (Material m : list) {
            if (m.getStatus() == Status.CANCELLED) continue;
            var v = view(m);
            planned = planned.add(v.plannedCost()); actual = actual.add(v.actualCost());
            if (m.getStatus() == Status.DELIVERED) delivered++;
        }
        long total = list.stream().filter(m -> m.getStatus() != Status.CANCELLED).count();
        return new Summary(Money.scale(planned), Money.scale(actual), Money.scale(actual.subtract(planned)), (int) total, delivered);
    }

    @Transactional(readOnly = true)
    public MaterialsResponse list(UUID projectId, AuthUser u) {
        access.require(projectId, u, Perm.VIEW);
        List<Material> all = repo.findByProjectIdOrderByCreatedAt(projectId);
        return new MaterialsResponse(all.stream().map(MaterialService::view).toList(), summarize(all));
    }

    @Transactional
    public MaterialView create(UUID projectId, MaterialInput in, AuthUser u) {
        access.require(projectId, u, Perm.MANAGE_MATERIALS);
        validate(in);
        Material m = new Material();
        m.setProjectId(projectId);
        fill(m, in);
        repo.save(m);
        events.publish(projectId, "MATERIAL_ADDED", "material", m.getId(), u.id(), "Добавлен материал: " + m.getName());
        return view(m);
    }

    @Transactional
    public MaterialView update(UUID id, MaterialInput in, AuthUser u) {
        Material m = load(id);
        access.require(m.getProjectId(), u, Perm.MANAGE_MATERIALS);
        if (m.getStatus() == Status.CANCELLED || m.getStatus() == Status.DELIVERED) throw ApiException.conflict("INVALID_STATE", "Material is " + m.getStatus());
        validate(in);
        if (in.plannedQty().compareTo(m.getReceivedQty()) < 0) throw ApiException.unprocessable("QTY_BELOW_RECEIVED", "Planned quantity is lower than already received");
        fill(m, in);
        events.publish(m.getProjectId(), "MATERIAL_UPDATED", "material", id, u.id(), "Изменён материал: " + m.getName());
        return view(repo.save(m));
    }

    @Transactional
    public MaterialView order(UUID id, AuthUser u) {
        Material m = load(id);
        access.require(m.getProjectId(), u, Perm.MANAGE_MATERIALS);
        if (m.getStatus() != Status.PLANNED) throw ApiException.conflict("INVALID_TRANSITION", "Only a planned material can be ordered");
        m.setStatus(Status.ORDERED);
        repo.save(m);
        events.publish(m.getProjectId(), "MATERIAL_ORDERED", "material", id, u.id(), "Заказан материал: " + m.getName());
        return view(m);
    }

    @Transactional
    public MaterialView receive(UUID id, ReceiveInput in, AuthUser u) {
        Material m = load(id);
        access.require(m.getProjectId(), u, Perm.MANAGE_MATERIALS);
        if (m.getStatus() != Status.ORDERED && m.getStatus() != Status.PARTIALLY_DELIVERED) throw ApiException.conflict("INVALID_TRANSITION", "Material must be ordered first");
        if (in.quantity() == null || in.quantity().signum() <= 0) throw ApiException.bad("VALIDATION_FAILED", "quantity must be positive");
        BigDecimal newTotal = m.getReceivedQty().add(in.quantity());
        if (newTotal.compareTo(m.getPlannedQty()) > 0) throw ApiException.unprocessable("OVER_DELIVERY", "Received quantity would exceed the planned quantity");
        m.setReceivedQty(newTotal);
        if (in.actualUnitPrice() != null) {
            if (in.actualUnitPrice().signum() < 0) throw ApiException.bad("VALIDATION_FAILED", "actualUnitPrice must not be negative");
            m.setActualUnitPrice(Money.scale(in.actualUnitPrice()));
        }
        m.setDeliveryDate(in.deliveryDate() != null ? in.deliveryDate() : LocalDate.now());
        m.setStatus(newTotal.compareTo(m.getPlannedQty()) == 0 ? Status.DELIVERED : Status.PARTIALLY_DELIVERED);
        repo.save(m);
        events.publish(m.getProjectId(), "MATERIAL_DELIVERED", "material", id, u.id(), "Поставка: " + m.getName() + " (" + newTotal.stripTrailingZeros().toPlainString() + "/" + m.getPlannedQty().stripTrailingZeros().toPlainString() + " " + m.getUnit() + ")");
        notifier.project(m.getProjectId(), u.id(), "MATERIAL_DELIVERED", "Материалы доставлены", m.getName());
        return view(m);
    }

    @Transactional
    public MaterialView cancel(UUID id, AuthUser u) {
        Material m = load(id);
        access.require(m.getProjectId(), u, Perm.MANAGE_MATERIALS);
        if (m.getReceivedQty().signum() > 0) throw ApiException.conflict("INVALID_TRANSITION", "Material was already (partly) delivered");
        m.setStatus(Status.CANCELLED);
        repo.save(m);
        events.publish(m.getProjectId(), "MATERIAL_CANCELLED", "material", id, u.id(), "Отменён материал: " + m.getName());
        return view(m);
    }

    private void fill(Material m, MaterialInput in) {
        m.setName(in.name().trim()); m.setUnit(in.unit() == null || in.unit().isBlank() ? "шт" : in.unit()); m.setPlannedQty(in.plannedQty());
        m.setUnitPrice(Money.scale(in.unitPrice())); m.setSupplier(in.supplier()); m.setDeliveryDate(in.deliveryDate());
    }

    private static void validate(MaterialInput in) {
        if (in.name() == null || in.name().isBlank()) throw ApiException.bad("VALIDATION_FAILED", "name is required");
        if (in.plannedQty() == null || in.plannedQty().signum() <= 0) throw ApiException.bad("VALIDATION_FAILED", "plannedQty must be positive");
        if (in.unitPrice() == null || in.unitPrice().signum() < 0) throw ApiException.bad("VALIDATION_FAILED", "unitPrice must not be negative");
    }

    private Material load(UUID id) { return repo.findById(id).orElseThrow(() -> ApiException.notFound("Material")); }
}
