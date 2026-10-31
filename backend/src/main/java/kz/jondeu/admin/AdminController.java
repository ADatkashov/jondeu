package kz.jondeu.admin;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kz.jondeu.common.ApiException;
import kz.jondeu.identity.*;
import kz.jondeu.masters.MasterService;
import kz.jondeu.payments.PaymentRepository;
import kz.jondeu.payments.PaymentService;
import kz.jondeu.projects.ProjectRepository;
import kz.jondeu.projects.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** All /api/v1/admin/** routes require PLATFORM_ADMIN / PLATFORM_SUPER_ADMIN (enforced in SecurityConfig). Every mutation is audited. */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Administration")
public class AdminController {
    private final UserRepository users;
    private final RefreshTokenRepository refresh;
    private final ProjectRepository projects;
    private final PaymentRepository payments;
    private final AuditLogRepository auditRepo;
    private final AuditService audit;
    private final MasterService masters;

    public AdminController(UserRepository users, RefreshTokenRepository refresh, ProjectRepository projects, PaymentRepository payments, AuditLogRepository auditRepo, AuditService audit, MasterService masters) {
        this.users = users; this.refresh = refresh; this.projects = projects; this.payments = payments; this.auditRepo = auditRepo; this.audit = audit; this.masters = masters;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Long> byStatus = new java.util.LinkedHashMap<>();
        for (ProjectStatus s : ProjectStatus.values()) byStatus.put(s.name(), projects.countByStatus(s));
        return Map.of("users", users.count(), "projects", projects.count(), "projectsByStatus", byStatus, "payments", payments.count());
    }

    @GetMapping("/users")
    public Page<AuthService.UserView> users(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return users.findAll(PageRequest.of(page, Math.min(size, 200), Sort.by("createdAt").descending())).map(u ->
            new AuthService.UserView(u.getId(), u.getPhone(), u.getEmail(), u.getFullName(), u.getRole(), u.getCity(), u.getLanguage(), u.getAvatarUrl()));
    }

    @PostMapping("/users/{id}/enabled")
    @Transactional
    public Map<String, Boolean> setEnabled(@PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
        AuthUser admin = CurrentUser.require();
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        if (u.getRole().isAdmin() && admin.role() != Role.PLATFORM_SUPER_ADMIN) throw ApiException.forbidden("Only a super admin can change administrators");
        if (u.getId().equals(admin.id())) throw ApiException.conflict("SELF_LOCK", "You cannot disable yourself");
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        u.setEnabled(enabled);
        users.save(u);
        if (!enabled) refresh.revokeAllForUser(id);
        audit.log(admin.id(), enabled ? "USER_ENABLED" : "USER_DISABLED", "user", id, u.getPhone());
        return Map.of("enabled", enabled);
    }

    @PostMapping("/masters/{id}/verify")
    public MasterService.MasterView verify(@PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
        return masters.verify(id, Boolean.TRUE.equals(body.get("verified")), CurrentUser.require());
    }

    @GetMapping("/payments")
    public Page<PaymentService.PaymentView> payments(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return payments.findAllByOrderByCreatedAtDesc(PageRequest.of(page, Math.min(size, 200))).map(p ->
            new PaymentService.PaymentView(p.getId(), p.getProjectId(), p.getStageId(), p.getPayerId(), p.getRequestedBy(), p.getType(), p.getMethod(), p.getAmount(), p.getCurrency(), p.getStatus(),
                p.getProvider(), p.getProviderRef(), p.getReference(), p.getNote(), p.getCreatedAt(), p.getPaidAt(), "SANDBOX".equals(p.getProvider())));
    }

    @GetMapping("/audit")
    public List<AuditLog> audit(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size) {
        return auditRepo.findAll(PageRequest.of(page, Math.min(size, 500), Sort.by("id").descending())).getContent();
    }
}
