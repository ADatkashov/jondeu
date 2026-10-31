package kz.jondeu.admin;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) { this.repo = repo; }

    public void log(UUID actor, String action, String entityType, UUID entityId, String detail) {
        AuditLog a = new AuditLog();
        a.setActorId(actor); a.setAction(action); a.setEntityType(entityType); a.setEntityId(entityId);
        a.setDetail(detail == null ? null : detail.substring(0, Math.min(detail.length(), 480)));
        repo.save(a);
    }
}
