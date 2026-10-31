package kz.jondeu.identity;

public enum Role {
    CUSTOMER, MASTER, CONTRACTOR, COMPANY_ADMIN, PROJECT_MANAGER, INSPECTOR, PLATFORM_ADMIN, PLATFORM_SUPER_ADMIN;

    public boolean isAdmin() { return this == PLATFORM_ADMIN || this == PLATFORM_SUPER_ADMIN; }
    public boolean isProfessional() { return this == MASTER || this == CONTRACTOR || this == COMPANY_ADMIN || this == PROJECT_MANAGER; }
}
