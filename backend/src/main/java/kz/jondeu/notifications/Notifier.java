package kz.jondeu.notifications;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kz.jondeu.projects.Project;
import kz.jondeu.projects.ProjectMember;
import kz.jondeu.projects.ProjectMemberRepository;
import kz.jondeu.projects.ProjectRepository;
import kz.jondeu.sync.ProjectEvents;
import kz.jondeu.sync.WsHub;
import org.springframework.stereotype.Service;

/** In-app notifications + WebSocket push. Email / mobile push are pluggable via {@link ExternalChannel}. */
@Service
public class Notifier {
    /** Extension point for FCM / APNs / email gateways. Implementations must be idempotent and retry-safe. */
    public interface ExternalChannel { void deliver(Notification n); }

    private final NotificationRepository repo;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final WsHub hub;
    private final List<ExternalChannel> channels;

    public Notifier(NotificationRepository repo, ProjectRepository projects, ProjectMemberRepository members, WsHub hub, List<ExternalChannel> channels) {
        this.repo = repo; this.projects = projects; this.members = members; this.hub = hub; this.channels = channels;
    }

    /** Notifies the owner and all members of the project except the actor. */
    public void project(UUID projectId, UUID actorId, String type, String title, String body) {
        Project p = projects.findById(projectId).orElse(null);
        if (p == null) return;
        Set<UUID> ids = new LinkedHashSet<>();
        ids.add(p.getOwnerId());
        members.findByProjectId(projectId).stream().map(ProjectMember::getUserId).forEach(ids::add);
        ids.remove(actorId);
        ids.forEach(uid -> user(uid, projectId, type, title, body));
    }

    public void user(UUID userId, UUID projectId, String type, String title, String body) {
        Notification n = new Notification();
        n.setUserId(userId); n.setProjectId(projectId); n.setType(type); n.setTitle(title); n.setBody(body);
        repo.save(n);
        Map<String, Object> msg = Map.of("kind", "notification", "id", n.getId(), "type", type, "title", title,
            "projectId", projectId == null ? "" : projectId.toString());
        ProjectEvents.afterCommit(() -> {
            hub.toUser(userId, msg);
            channels.forEach(c -> { try { c.deliver(n); } catch (RuntimeException ignored) { /* external delivery must never break the business tx */ } });
        });
    }
}
