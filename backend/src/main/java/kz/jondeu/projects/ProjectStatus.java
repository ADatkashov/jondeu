package kz.jondeu.projects;

public enum ProjectStatus {
    DRAFT, SUBMITTED, UNDER_REVIEW, PROPOSALS_RECEIVED, CONTRACTOR_SELECTED, IN_PROGRESS, COMPLETED, CANCELLED;

    public boolean isOpenForProposals() { return this == SUBMITTED || this == UNDER_REVIEW || this == PROPOSALS_RECEIVED; }
    public boolean isActive() { return this == CONTRACTOR_SELECTED || this == IN_PROGRESS; }
}
