package kz.jondeu.projects;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kz.jondeu.identity.CurrentUser;
import kz.jondeu.sync.ProjectEvent;
import kz.jondeu.sync.ProjectEventRepository;
import kz.jondeu.sync.ProjectEvents;
import kz.jondeu.projects.ProjectRole.Perm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Projects")
public class ProjectController {
    private final ProjectService svc;
    private final OverviewService overview;
    private final ProjectAccess access;
    private final ProjectEventRepository eventRepo;

    public ProjectController(ProjectService svc, OverviewService overview, ProjectAccess access, ProjectEventRepository eventRepo) {
        this.svc = svc; this.overview = overview; this.access = access; this.eventRepo = eventRepo;
    }

    @PostMapping("/projects")
    public ProjectService.ProjectView create(@RequestBody ProjectService.ProjectInput in) { return svc.create(in, CurrentUser.require()); }

    @GetMapping("/projects")
    public Page<ProjectService.ProjectView> list(@RequestParam(defaultValue = "false") boolean archived, @RequestParam(defaultValue = "false") boolean all,
                                                 @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return svc.list(CurrentUser.require(), archived, all, page, size);
    }

    @GetMapping("/projects/{id}")
    public ProjectService.ProjectView get(@PathVariable UUID id) { return svc.get(id, CurrentUser.require()); }

    @PatchMapping("/projects/{id}")
    public ProjectService.ProjectView update(@PathVariable UUID id, @RequestBody ProjectService.ProjectInput in) { return svc.update(id, in, CurrentUser.require()); }

    @PostMapping("/projects/{id}/submit")
    public ProjectService.ProjectView submit(@PathVariable UUID id) { return svc.submit(id, CurrentUser.require()); }

    @PostMapping("/projects/{id}/cancel")
    public ProjectService.ProjectView cancel(@PathVariable UUID id) { return svc.cancel(id, CurrentUser.require()); }

    @PostMapping("/projects/{id}/complete")
    public ProjectService.ProjectView complete(@PathVariable UUID id) { return svc.complete(id, CurrentUser.require()); }

    @PostMapping("/projects/{id}/archive")
    public ProjectService.ProjectView archive(@PathVariable UUID id, @RequestParam(defaultValue = "true") boolean archived) { return svc.archive(id, archived, CurrentUser.require()); }

    /** One aggregated, server-computed snapshot for dashboards: budget, progress, schedule, quality, counters. */
    @GetMapping("/projects/{id}/overview")
    public OverviewService.Overview overview(@PathVariable UUID id) { return overview.overview(id, CurrentUser.require()); }

    /** Sync catch-up: events after a cursor. Clients call it after a WebSocket reconnect. */
    @GetMapping("/projects/{id}/events")
    public List<Map<String, Object>> events(@PathVariable UUID id, @RequestParam(defaultValue = "0") long after, @RequestParam(defaultValue = "200") int limit) {
        access.require(id, CurrentUser.require(), Perm.VIEW);
        return eventRepo.findByProjectIdAndSeqGreaterThanOrderBySeqAsc(id, after, PageRequest.of(0, Math.min(limit, 500))).stream().map(ProjectEvents::toMessage).toList();
    }

    /** Activity feed (latest first). */
    @GetMapping("/projects/{id}/activity")
    public List<Map<String, Object>> activity(@PathVariable UUID id, @RequestParam(defaultValue = "30") int limit) {
        access.require(id, CurrentUser.require(), Perm.VIEW);
        return eventRepo.findByProjectIdOrderBySeqDesc(id, PageRequest.of(0, Math.min(limit, 200))).stream().map(ProjectEvents::toMessage).toList();
    }

    // proposals
    @GetMapping("/requests/open")
    public Page<ProjectService.OpenRequestView> open(@RequestParam(required = false) String category, @RequestParam(required = false) String city,
                                                     @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return svc.openRequests(CurrentUser.require(), category, city, page, size);
    }

    @PostMapping("/projects/{id}/proposals")
    public ProjectService.ProposalView propose(@PathVariable UUID id, @RequestBody ProjectService.ProposalInput in) { return svc.propose(id, in, CurrentUser.require()); }

    @GetMapping("/projects/{id}/proposals")
    public List<ProjectService.ProposalView> proposals(@PathVariable UUID id) { return svc.proposals(id, CurrentUser.require()); }

    @GetMapping("/proposals/mine")
    public List<ProjectService.ProposalView> mine() { return svc.myProposals(CurrentUser.require()); }

    @PostMapping("/proposals/{id}/accept")
    public ProjectService.ProposalView accept(@PathVariable UUID id) { return svc.accept(id, CurrentUser.require()); }

    @PostMapping("/proposals/{id}/reject")
    public ProjectService.ProposalView reject(@PathVariable UUID id) { return svc.reject(id, CurrentUser.require()); }

    // members
    @GetMapping("/projects/{id}/members")
    public List<ProjectService.MemberView> members(@PathVariable UUID id) { return svc.members(id, CurrentUser.require()); }

    @PostMapping("/projects/{id}/members")
    public ProjectService.MemberView addMember(@PathVariable UUID id, @RequestBody ProjectService.MemberInput in) { return svc.addMember(id, in, CurrentUser.require()); }

    @DeleteMapping("/projects/{id}/members/{userId}")
    public Map<String, Boolean> removeMember(@PathVariable UUID id, @PathVariable UUID userId) { svc.removeMember(id, userId, CurrentUser.require()); return Map.of("ok", true); }
}
