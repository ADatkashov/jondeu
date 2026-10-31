package kz.jondeu.projects;

import java.util.EnumSet;
import java.util.Set;

/** Role of a user inside one project. Drives the permission matrix. */
public enum ProjectRole {
    OWNER(EnumSet.of(Perm.VIEW, Perm.MANAGE_PROJECT, Perm.MANAGE_MEMBERS, Perm.APPROVE_ESTIMATE, Perm.APPROVE_STAGE, Perm.PAY,
        Perm.UPLOAD, Perm.MANAGE_CALENDAR, Perm.MANAGE_MATERIALS, Perm.CHAT)),
    CONTRACTOR(EnumSet.of(Perm.VIEW, Perm.EDIT_ESTIMATE, Perm.UPDATE_STAGE, Perm.MANAGE_MATERIALS, Perm.REQUEST_PAYMENT, Perm.UPLOAD,
        Perm.MANAGE_CALENDAR, Perm.MANAGE_MEMBERS, Perm.CHAT)),
    PROJECT_MANAGER(EnumSet.of(Perm.VIEW, Perm.EDIT_ESTIMATE, Perm.UPDATE_STAGE, Perm.MANAGE_MATERIALS, Perm.REQUEST_PAYMENT, Perm.UPLOAD,
        Perm.MANAGE_CALENDAR, Perm.MANAGE_MEMBERS, Perm.CHAT)),
    MASTER(EnumSet.of(Perm.VIEW, Perm.UPDATE_STAGE, Perm.UPLOAD, Perm.CHAT)),
    INSPECTOR(EnumSet.of(Perm.VIEW, Perm.INSPECT, Perm.APPROVE_STAGE, Perm.UPLOAD, Perm.CHAT)),
    /** Platform administrators: full operational visibility, but cannot pay on behalf of a customer. */
    ADMIN(EnumSet.complementOf(EnumSet.of(Perm.PAY)));

    public enum Perm {
        VIEW, MANAGE_PROJECT, MANAGE_MEMBERS, EDIT_ESTIMATE, APPROVE_ESTIMATE, UPDATE_STAGE, APPROVE_STAGE, MANAGE_MATERIALS,
        PAY, REQUEST_PAYMENT, UPLOAD, INSPECT, MANAGE_CALENDAR, CHAT
    }

    private final Set<Perm> perms;

    ProjectRole(Set<Perm> perms) { this.perms = perms; }

    public boolean can(Perm p) { return perms.contains(p); }
}
