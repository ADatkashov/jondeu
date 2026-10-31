package kz.jondeu.projects;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import kz.jondeu.common.ApiException;
import kz.jondeu.common.Money;
import kz.jondeu.identity.*;
import kz.jondeu.masters.MasterService;
import kz.jondeu.masters.ServiceCategory;
import kz.jondeu.notifications.Notifier;
import kz.jondeu.payments.FinanceService;
import kz.jondeu.projects.ProjectRole.Perm;
import kz.jondeu.stages.Stage;
import kz.jondeu.stages.StageRepository;
import kz.jondeu.stages.StageStatus;
import kz.jondeu.sync.ProjectEvents;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {
    public record ProjectInput(String name, String propertyType, Integer rooms, BigDecimal areaM2, String address, String city, String description, String category,
                               BigDecimal budgetMin, BigDecimal budgetMax, Boolean materialsIncluded, String urgency, LocalDate startDate, LocalDate endDate) {}
    public record ProjectView(UUID id, UUID ownerId, String ownerName, UUID contractorId, String contractorName, String name, String propertyType, Integer rooms, BigDecimal areaM2,
                              String address, String city, String description, String category, ProjectStatus status, BigDecimal budgetMin, BigDecimal budgetMax, BigDecimal approvedBudget,
                              String currency, boolean materialsIncluded, String urgency, LocalDate startDate, LocalDate endDate, boolean archived, boolean demo,
                              ProjectRole myRole, Instant createdAt, Instant updatedAt) {}
    public record ProposalInput(BigDecimal price, Integer durationDays, String message) {}
    public record ProposalView(UUID id, UUID projectId, UUID masterId, String masterName, BigDecimal price, String currency, int durationDays, String message, Proposal.Status status, Instant createdAt) {}
    public record MemberInput(String phone, ProjectRole role) {}
    public record MemberView(UUID userId, String fullName, String phone, ProjectRole role) {}
    public record OpenRequestView(UUID id, String name, String category, String propertyType, BigDecimal areaM2, String city, BigDecimal budgetMin, BigDecimal budgetMax, String description,
                                  ProjectStatus status, Instant createdAt, long proposalsCount, Proposal.Status myProposal) {}

    private static final Set<ProjectRole> ASSIGNABLE = EnumSet.of(ProjectRole.PROJECT_MANAGER, ProjectRole.MASTER, ProjectRole.INSPECTOR);

    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final ProposalRepository proposals;
    private final UserRepository users;
    private final ProjectAccess access;
    private final ProjectEvents events;
    private final Notifier notifier;
    private final StageRepository stages;
    private final FinanceService finance;
    private final MasterService masters;

    public ProjectService(ProjectRepository projects, ProjectMemberRepository members, ProposalRepository proposals, UserRepository users, ProjectAccess access, ProjectEvents events,
                          Notifier notifier, StageRepository stages, FinanceService finance, MasterService masters) {
        this.projects = projects; this.members = members; this.proposals = proposals; this.users = users; this.access = access; this.events = events;
        this.notifier = notifier; this.stages = stages; this.finance = finance; this.masters = masters;
    }

    // ---------- CRUD
    @Transactional
    public ProjectView create(ProjectInput in, AuthUser u) {
        if (u.isAdmin()) throw ApiException.forbidden("Administrators do not own renovation projects");
        Project p = new Project();
        p.setOwnerId(u.id());
        apply(p, in, true);
        p.setStatus(ProjectStatus.DRAFT);
        projects.save(p);
        events.publish(p.getId(), "PROJECT_CREATED", "project", p.getId(), u.id(), "Создан проект «" + p.getName() + "»");
        return view(p, u);
    }

    @Transactional
    public ProjectView update(UUID id, ProjectInput in, AuthUser u) {
        Project p = access.require(id, u, Perm.MANAGE_PROJECT);
        if (p.getStatus() == ProjectStatus.COMPLETED || p.getStatus() == ProjectStatus.CANCELLED) throw ApiException.conflict("PROJECT_STATE", "Project is " + p.getStatus());
        apply(p, in, false);
        p.setUpdatedAt(Instant.now());
        projects.save(p);
        events.publish(id, "PROJECT_UPDATED", "project", id, u.id(), "Проект обновлён");
        return view(p, u);
    }

    private void apply(Project p, ProjectInput in, boolean creating) {
        if (creating || in.name() != null) {
            if (in.name() == null || in.name().isBlank()) throw ApiException.bad("VALIDATION_FAILED", "name is required");
            p.setName(in.name().trim());
        }
        if (creating || in.propertyType() != null) {
            String t = in.propertyType() == null ? "APARTMENT" : in.propertyType();
            if (!t.equals("APARTMENT") && !t.equals("HOUSE")) throw ApiException.bad("VALIDATION_FAILED", "propertyType must be APARTMENT or HOUSE");
            p.setPropertyType(t);
        }
        if (creating || in.areaM2() != null) {
            if (in.areaM2() == null || in.areaM2().signum() <= 0 || in.areaM2().compareTo(new BigDecimal("100000")) > 0) throw ApiException.bad("VALIDATION_FAILED", "areaM2 must be positive");
            p.setAreaM2(in.areaM2());
        }
        if (in.rooms() != null) { if (in.rooms() < 0 || in.rooms() > 100) throw ApiException.bad("VALIDATION_FAILED", "rooms out of range"); p.setRooms(in.rooms()); }
        if (in.address() != null) p.setAddress(in.address());
        if (in.city() != null) p.setCity(in.city());
        if (in.description() != null) p.setDescription(in.description());
        if (in.category() != null) {
            if (!ServiceCategory.valid(in.category())) throw ApiException.bad("INVALID_CATEGORY", "Unknown category");
            p.setCategory(in.category());
        }
        if (in.budgetMin() != null) p.setBudgetMin(Money.scale(in.budgetMin()));
        if (in.budgetMax() != null) p.setBudgetMax(Money.scale(in.budgetMax()));
        if (p.getBudgetMin() != null && p.getBudgetMax() != null && p.getBudgetMin().compareTo(p.getBudgetMax()) > 0) throw ApiException.bad("INVALID_BUDGET", "budgetMin must not exceed budgetMax");
        if ((p.getBudgetMin() != null && p.getBudgetMin().signum() < 0)) throw ApiException.bad("INVALID_BUDGET", "budget must not be negative");
        if (in.materialsIncluded() != null) p.setMaterialsIncluded(in.materialsIncluded());
        if (in.urgency() != null) p.setUrgency(in.urgency());
        if (in.startDate() != null) p.setStartDate(in.startDate());
        if (in.endDate() != null) p.setEndDate(in.endDate());
        if (p.getStartDate() != null && p.getEndDate() != null && p.getEndDate().isBefore(p.getStartDate())) throw ApiException.bad("INVALID_DATES", "endDate must not be before startDate");
    }

    @Transactional(readOnly = true)
    public Page<ProjectView> list(AuthUser u, boolean archived, boolean all, int page, int size) {
        PageRequest pr = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "updatedAt"));
        if (all && u.isAdmin()) return projects.findAll(pr).map(p -> view(p, u));
        return projects.findAccessible(u.id(), archived, pr).map(p -> view(p, u));
    }

    @Transactional(readOnly = true)
    public ProjectView get(UUID id, AuthUser u) { return view(access.require(id, u, Perm.VIEW), u); }

    // ---------- lifecycle
    @Transactional
    public ProjectView submit(UUID id, AuthUser u) {
        Project p = access.require(id, u, Perm.MANAGE_PROJECT);
        if (p.getStatus() != ProjectStatus.DRAFT) throw ApiException.conflict("INVALID_TRANSITION", "Only a draft request can be submitted");
        Map<String, String> missing = new LinkedHashMap<>();
        if (p.getCategory() == null) missing.put("category", "required");
        if (p.getDescription() == null || p.getDescription().isBlank()) missing.put("description", "required");
        if (p.getAddress() == null || p.getAddress().isBlank()) missing.put("address", "required");
        if (p.getBudgetMax() == null) missing.put("budgetMax", "required");
        if (!missing.isEmpty()) throw ApiException.unprocessable("INCOMPLETE_REQUEST", "Fill in required fields: " + String.join(", ", missing.keySet()));
        p.setStatus(ProjectStatus.SUBMITTED);
        projects.save(p);
        events.publish(id, "REQUEST_SUBMITTED", "project", id, u.id(), "Заявка отправлена мастерам");
        return view(p, u);
    }

    @Transactional
    public ProjectView cancel(UUID id, AuthUser u) {
        Project p = access.require(id, u, Perm.MANAGE_PROJECT);
        if (p.getStatus() == ProjectStatus.COMPLETED || p.getStatus() == ProjectStatus.CANCELLED) throw ApiException.conflict("INVALID_TRANSITION", "Project is " + p.getStatus());
        if (finance.netPaid(id).signum() > 0 && !u.isAdmin()) throw ApiException.conflict("HAS_PAYMENTS", "Project has payments; refund them before cancelling");
        p.setStatus(ProjectStatus.CANCELLED);
        projects.save(p);
        events.publish(id, "PROJECT_CANCELLED", "project", id, u.id(), "Проект отменён");
        notifier.project(id, u.id(), "PROJECT_CANCELLED", "Проект отменён", p.getName());
        return view(p, u);
    }

    /** Final handover approval by the customer. */
    @Transactional
    public ProjectView complete(UUID id, AuthUser u) {
        Project p = access.requireLocked(id, u, Perm.APPROVE_STAGE);
        if (!p.getOwnerId().equals(u.id()) && !u.isAdmin()) throw ApiException.forbidden("Only the customer can accept final handover");
        if (p.getStatus() != ProjectStatus.IN_PROGRESS) throw ApiException.conflict("INVALID_TRANSITION", "Project is " + p.getStatus());
        List<Stage> list = stages.findByProjectIdOrderByPosition(id);
        if (list.isEmpty() || list.stream().anyMatch(s -> s.getStatus() != StageStatus.COMPLETED)) throw ApiException.conflict("STAGES_INCOMPLETE", "All stages must be completed and accepted");
        BigDecimal outstanding = p.getApprovedBudget().subtract(finance.netPaid(id));
        if (outstanding.signum() > 0) throw ApiException.conflict("OUTSTANDING_BALANCE", "Outstanding balance " + Money.scale(outstanding) + " " + p.getCurrency() + " must be paid first");
        p.setStatus(ProjectStatus.COMPLETED);
        p.setEndDate(LocalDate.now());
        projects.save(p);
        if (p.getContractorId() != null) masters.incrementCompleted(p.getContractorId());
        events.publish(id, "PROJECT_COMPLETED", "project", id, u.id(), "Проект завершён, объект принят");
        notifier.project(id, u.id(), "PROJECT_COMPLETED", "Проект завершён", p.getName());
        return view(p, u);
    }

    @Transactional
    public ProjectView archive(UUID id, boolean archived, AuthUser u) {
        Project p = access.require(id, u, Perm.MANAGE_PROJECT);
        if (archived && p.getStatus() != ProjectStatus.COMPLETED && p.getStatus() != ProjectStatus.CANCELLED) throw ApiException.conflict("INVALID_TRANSITION", "Only completed or cancelled projects can be archived");
        p.setArchived(archived);
        projects.save(p);
        events.publish(id, archived ? "PROJECT_ARCHIVED" : "PROJECT_UNARCHIVED", "project", id, u.id(), archived ? "Проект в архиве" : "Проект восстановлен из архива");
        return view(p, u);
    }

    // ---------- proposals
    @Transactional(readOnly = true)
    public Page<OpenRequestView> openRequests(AuthUser u, String category, String city, int page, int size) {
        if (!u.role().isProfessional() && !u.isAdmin()) throw ApiException.forbidden("Only professionals can browse open requests");
        Page<Project> pg = projects.findByStatusIn(List.of(ProjectStatus.SUBMITTED, ProjectStatus.UNDER_REVIEW, ProjectStatus.PROPOSALS_RECEIVED), PageRequest.of(page, Math.min(size, 50), Sort.by(Sort.Direction.DESC, "createdAt")));
        Map<UUID, Proposal.Status> mine = proposals.findByMasterIdOrderByCreatedAtDesc(u.id()).stream().collect(Collectors.toMap(Proposal::getProjectId, Proposal::getStatus, (a, b) -> a));
        return pg.map(p -> new OpenRequestView(p.getId(), p.getName(), p.getCategory(), p.getPropertyType(), p.getAreaM2(), p.getCity(), p.getBudgetMin(), p.getBudgetMax(), p.getDescription(),
            p.getStatus(), p.getCreatedAt(), proposals.countByProjectId(p.getId()), mine.get(p.getId())));
    }

    @Transactional
    public ProposalView propose(UUID projectId, ProposalInput in, AuthUser u) {
        if (!u.role().isProfessional()) throw ApiException.forbidden("Only professionals can submit proposals");
        Project p = projects.findById(projectId).orElseThrow(() -> ApiException.notFound("Project"));
        if (!p.getStatus().isOpenForProposals()) throw ApiException.conflict("REQUEST_CLOSED", "The request does not accept proposals");
        if (p.getOwnerId().equals(u.id())) throw ApiException.forbidden("You cannot bid on your own request");
        if (in.price() == null || in.price().signum() <= 0) throw ApiException.bad("VALIDATION_FAILED", "price must be positive");
        if (in.durationDays() == null || in.durationDays() <= 0 || in.durationDays() > 3650) throw ApiException.bad("VALIDATION_FAILED", "durationDays must be positive");
        if (proposals.findByProjectIdAndMasterId(projectId, u.id()).isPresent()) throw ApiException.conflict("PROPOSAL_EXISTS", "You already submitted a proposal");
        Proposal pr = new Proposal();
        pr.setProjectId(projectId); pr.setMasterId(u.id()); pr.setPrice(Money.scale(in.price())); pr.setCurrency(p.getCurrency()); pr.setDurationDays(in.durationDays()); pr.setMessage(in.message());
        proposals.save(pr);
        p.setStatus(ProjectStatus.PROPOSALS_RECEIVED);
        projects.save(p);
        events.publish(projectId, "PROPOSAL_RECEIVED", "proposal", pr.getId(), u.id(), "Новое предложение от " + u.fullName() + ": " + pr.getPrice() + " " + pr.getCurrency());
        notifier.user(p.getOwnerId(), projectId, "NEW_PROPOSAL", "Новое предложение", u.fullName() + " — " + pr.getPrice() + " " + pr.getCurrency());
        return proposalView(pr, u.fullName());
    }

    @Transactional(readOnly = true)
    public List<ProposalView> proposals(UUID projectId, AuthUser u) {
        Project p = access.require(projectId, u, Perm.VIEW);
        boolean owner = p.getOwnerId().equals(u.id()) || u.isAdmin();
        List<Proposal> list = proposals.findByProjectIdOrderByPriceAsc(projectId);
        if (!owner) list = list.stream().filter(x -> x.getMasterId().equals(u.id())).toList();
        Map<UUID, User> names = users.findByIdIn(list.stream().map(Proposal::getMasterId).collect(Collectors.toSet())).stream().collect(Collectors.toMap(User::getId, x -> x));
        return list.stream().map(x -> proposalView(x, names.get(x.getMasterId()) == null ? null : names.get(x.getMasterId()).getFullName())).toList();
    }

    @Transactional(readOnly = true)
    public List<ProposalView> myProposals(AuthUser u) {
        return proposals.findByMasterIdOrderByCreatedAtDesc(u.id()).stream().map(x -> proposalView(x, u.fullName())).toList();
    }

    @Transactional
    public ProposalView accept(UUID proposalId, AuthUser u) {
        Proposal pr = proposals.findById(proposalId).orElseThrow(() -> ApiException.notFound("Proposal"));
        Project p = access.requireLocked(pr.getProjectId(), u, Perm.MANAGE_PROJECT);
        if (!p.getOwnerId().equals(u.id())) throw ApiException.forbidden("Only the customer can accept a proposal");
        if (!p.getStatus().isOpenForProposals()) throw ApiException.conflict("REQUEST_CLOSED", "A contractor is already selected");
        if (pr.getStatus() != Proposal.Status.PENDING) throw ApiException.conflict("INVALID_TRANSITION", "Proposal is " + pr.getStatus());
        pr.setStatus(Proposal.Status.ACCEPTED);
        proposals.findByProjectIdOrderByPriceAsc(p.getId()).stream().filter(x -> !x.getId().equals(pr.getId()) && x.getStatus() == Proposal.Status.PENDING).forEach(x -> {
            x.setStatus(Proposal.Status.REJECTED);
            notifier.user(x.getMasterId(), p.getId(), "PROPOSAL_REJECTED", "Заказчик выбрал другого мастера", p.getName());
        });
        p.setContractorId(pr.getMasterId());
        p.setStatus(ProjectStatus.CONTRACTOR_SELECTED);
        if (members.findByProjectIdAndUserId(p.getId(), pr.getMasterId()).isEmpty()) members.save(new ProjectMember(p.getId(), pr.getMasterId(), ProjectRole.CONTRACTOR));
        projects.save(p);
        events.publish(p.getId(), "CONTRACTOR_SELECTED", "proposal", pr.getId(), u.id(), "Выбран подрядчик");
        notifier.user(pr.getMasterId(), p.getId(), "PROPOSAL_ACCEPTED", "Ваше предложение принято", p.getName());
        return proposalView(pr, users.findById(pr.getMasterId()).map(User::getFullName).orElse(null));
    }

    @Transactional
    public ProposalView reject(UUID proposalId, AuthUser u) {
        Proposal pr = proposals.findById(proposalId).orElseThrow(() -> ApiException.notFound("Proposal"));
        Project p = access.require(pr.getProjectId(), u, Perm.MANAGE_PROJECT);
        if (!p.getOwnerId().equals(u.id())) throw ApiException.forbidden("Only the customer can reject a proposal");
        if (pr.getStatus() != Proposal.Status.PENDING) throw ApiException.conflict("INVALID_TRANSITION", "Proposal is " + pr.getStatus());
        pr.setStatus(Proposal.Status.REJECTED);
        proposals.save(pr);
        events.publish(p.getId(), "PROPOSAL_REJECTED", "proposal", pr.getId(), u.id(), "Предложение отклонено");
        notifier.user(pr.getMasterId(), p.getId(), "PROPOSAL_REJECTED", "Предложение отклонено", p.getName());
        return proposalView(pr, null);
    }

    // ---------- members
    @Transactional(readOnly = true)
    public List<MemberView> members(UUID projectId, AuthUser u) {
        Project p = access.require(projectId, u, Perm.VIEW);
        List<ProjectMember> ms = members.findByProjectId(projectId);
        Set<UUID> ids = ms.stream().map(ProjectMember::getUserId).collect(Collectors.toSet());
        ids.add(p.getOwnerId());
        Map<UUID, User> names = users.findByIdIn(ids).stream().collect(Collectors.toMap(User::getId, x -> x));
        List<MemberView> out = new ArrayList<>();
        out.add(new MemberView(p.getOwnerId(), names.get(p.getOwnerId()).getFullName(), null, ProjectRole.OWNER));
        ms.forEach(m -> out.add(new MemberView(m.getUserId(), names.get(m.getUserId()).getFullName(), null, m.getMemberRole())));
        return out;
    }

    @Transactional
    public MemberView addMember(UUID projectId, MemberInput in, AuthUser u) {
        Project p = access.require(projectId, u, Perm.MANAGE_MEMBERS);
        if (in.role() == null || !ASSIGNABLE.contains(in.role())) throw ApiException.bad("INVALID_ROLE", "Assignable roles: " + ASSIGNABLE);
        User target = users.findByPhone(AuthService.normalizePhone(in.phone())).filter(User::isEnabled).orElseThrow(() -> ApiException.notFound("User"));
        if (target.getId().equals(p.getOwnerId())) throw ApiException.conflict("ALREADY_MEMBER", "User is the project owner");
        if (in.role() == ProjectRole.INSPECTOR && target.getRole() != Role.INSPECTOR && !target.getRole().isAdmin()) throw ApiException.unprocessable("ROLE_MISMATCH", "User is not an inspector");
        if (in.role() != ProjectRole.INSPECTOR && !target.getRole().isProfessional()) throw ApiException.unprocessable("ROLE_MISMATCH", "User is not a professional");
        ProjectMember m = members.findByProjectIdAndUserId(projectId, target.getId()).orElse(new ProjectMember(projectId, target.getId(), in.role()));
        if (m.getMemberRole() == ProjectRole.CONTRACTOR) throw ApiException.conflict("ALREADY_MEMBER", "User is the contractor");
        m.setMemberRole(in.role());
        members.save(m);
        events.publish(projectId, "MEMBER_ADDED", "member", target.getId(), u.id(), target.getFullName() + " добавлен(а) в проект");
        notifier.user(target.getId(), projectId, "ADDED_TO_PROJECT", "Вас добавили в проект", p.getName());
        return new MemberView(target.getId(), target.getFullName(), null, in.role());
    }

    @Transactional
    public void removeMember(UUID projectId, UUID userId, AuthUser u) {
        Project p = access.require(projectId, u, Perm.MANAGE_MEMBERS);
        ProjectMember m = members.findByProjectIdAndUserId(projectId, userId).orElseThrow(() -> ApiException.notFound("Member"));
        if (m.getMemberRole() == ProjectRole.CONTRACTOR) throw ApiException.conflict("CONTRACTOR_LOCKED", "The selected contractor cannot be removed");
        members.delete(m);
        events.publish(projectId, "MEMBER_REMOVED", "member", userId, u.id(), "Участник удалён из проекта");
    }

    // ---------- mapping
    public ProjectView view(Project p, AuthUser viewer) {
        Map<UUID, User> names = users.findByIdIn(p.getContractorId() == null ? List.of(p.getOwnerId()) : List.of(p.getOwnerId(), p.getContractorId())).stream().collect(Collectors.toMap(User::getId, x -> x));
        User owner = names.get(p.getOwnerId());
        User c = p.getContractorId() == null ? null : names.get(p.getContractorId());
        return new ProjectView(p.getId(), p.getOwnerId(), owner == null ? null : owner.getFullName(), p.getContractorId(), c == null ? null : c.getFullName(), p.getName(), p.getPropertyType(), p.getRooms(),
            p.getAreaM2(), p.getAddress(), p.getCity(), p.getDescription(), p.getCategory(), p.getStatus(), p.getBudgetMin(), p.getBudgetMax(), Money.nz(p.getApprovedBudget()), p.getCurrency(),
            p.isMaterialsIncluded(), p.getUrgency(), p.getStartDate(), p.getEndDate(), p.isArchived(), p.isDemo(), access.roleOf(p, viewer).orElse(null), p.getCreatedAt(), p.getUpdatedAt());
    }

    private ProposalView proposalView(Proposal x, String masterName) {
        return new ProposalView(x.getId(), x.getProjectId(), x.getMasterId(), masterName, x.getPrice(), x.getCurrency(), x.getDurationDays(), x.getMessage(), x.getStatus(), x.getCreatedAt());
    }
}
