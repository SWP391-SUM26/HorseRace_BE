package com.SWP391.horserace.onboarding.entity;

/** Lifecycle status of a membership application. Matches DB CHECK on status. */
public enum ApplicationStatus {
    PENDING, UNDER_REVIEW, INFO_REQUESTED, APPROVED, REJECTED;

    /**
     * Whether a reviewer may still take a decision from this status (not yet terminal).
     * APPROVED/REJECTED are terminal. Shared by the referee onboarding flow and the admin
     * jockey approve/reject so the "already decided" guard has a single source of truth.
     */
    public boolean isDecidable() {
        return this == PENDING || this == UNDER_REVIEW || this == INFO_REQUESTED;
    }
}
