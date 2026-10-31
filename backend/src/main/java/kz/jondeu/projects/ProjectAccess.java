package kz.jondeu.projects;

import java.util.UUID;
import kz.jondeu.common.ApiException;
import kz.jondeu.identity.AuthUser;
import kz.jondeu.projects.ProjectRole.Perm;
import org.springframework.stereotype.Service;

/**
 * Single place where project-level authorization is decided. Every service operation that touches
 * project data goes through {@link #require}. A user without any relation to the project gets 404
 * (existence is not leaked); a related user lacking the permission gets 403.
 */
@Service
public class ProjectAccess {
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;

    public ProjectAccess(ProjectRepository projects, ProjectMemberRepository members) { this.projects = projects; this.members = members; }

    public java.util.Optional<ProjectRole> roleOf(Project p, AuthUser u) {
        if (u.isAdmin()) return java.util.Optional.of(ProjectRole.ADMIN);
        if (p.getOwnerId().equals(u.id())) return java.util.Optional.of(ProjectRole.OWNER);
        return members.findByProjectIdAndUserId(p.getId(), u.id()).map(ProjectMember::getMemberRole);
    }

    public Project require(UUID projectId, AuthUser u, Perm perm) {
        Project p = projects.findById(projectId).orElseThrow(() -> ApiException.notFound("Project"));
        ProjectRole r = roleOf(p, u).orElseThrow(() -> ApiException.notFound("Project"));
        if (!r.can(perm)) throw ApiException.forbidden("Missing permission " + perm);
        return p;
    }

    /** Same as {@link #require} but takes a pessimistic lock on the project row (financial operations). */
    public Project requireLocked(UUID projectId, AuthUser u, Perm perm) {
        Project p = projects.lockById(projectId).orElseThrow(() -> ApiException.notFound("Project"));
        ProjectRole r = roleOf(p, u).orElseThrow(() -> ApiException.notFound("Project"));
        if (!r.can(perm)) throw ApiException.forbidden("Missing permission " + perm);
        return p;
    }

    public ProjectRole roleOrThrow(Project p, AuthUser u) {
        return roleOf(p, u).orElseThrow(() -> ApiException.notFound("Project"));
    }

    public boolean can(UUID projectId, AuthUser u, Perm perm) {
        return projects.findById(projectId).flatMap(p -> roleOf(p, u)).map(r -> r.can(perm)).orElse(false);
    }
}
