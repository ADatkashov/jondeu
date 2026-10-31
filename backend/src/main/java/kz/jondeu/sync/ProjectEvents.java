package kz.jondeu.sync;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The heart of cross-client synchronization. Every domain mutation calls {@link #publish} inside its
 * transaction: the event is persisted atomically with the change (so the log never lies) and the
 * WebSocket broadcast is only sent after commit (so clients never refetch uncommitted data).
 * Clients that were offline catch up with GET /projects/{id}/events?after=seq.
 */
@Service
public class ProjectEvents {
    private final ProjectEventRepository repo;
    private final WsHub hub;

    public ProjectEvents(ProjectEventRepository repo, WsHub hub) { this.repo = repo; this.hub = hub; }

    public ProjectEvent publish(UUID projectId, String type, String entityType, UUID entityId, UUID actorId, String message) {
        ProjectEvent e = new ProjectEvent();
        e.setProjectId(projectId); e.setType(type); e.setEntityType(entityType); e.setEntityId(entityId);
        e.setActorId(actorId); e.setMessage(message); e.setCreatedAt(Instant.now());
        repo.save(e);
        Map<String, Object> msg = toMessage(e);
        afterCommit(() -> hub.toProject(projectId, msg));
        return e;
    }

    public static Map<String, Object> toMessage(ProjectEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", "project.event");
        m.put("seq", e.getSeq());
        m.put("projectId", e.getProjectId());
        m.put("type", e.getType());
        m.put("entityType", e.getEntityType());
        if (e.getEntityId() != null) m.put("entityId", e.getEntityId());
        if (e.getActorId() != null) m.put("actorId", e.getActorId());
        if (e.getMessage() != null) m.put("message", e.getMessage());
        m.put("createdAt", e.getCreatedAt());
        return m;
    }

    public static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { r.run(); }
            });
        } else {
            r.run();
        }
    }
}
