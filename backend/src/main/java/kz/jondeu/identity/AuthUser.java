package kz.jondeu.identity;

import java.util.UUID;

/** Authenticated principal placed in the SecurityContext. */
public record AuthUser(UUID id, Role role, String fullName) {
    public boolean isAdmin() { return role.isAdmin(); }
}
