package com.SWP391.horserace.onboarding.entity;

/** Lifecycle status of a membership application. Matches DB CHECK on status. */
public enum ApplicationStatus {
    PENDING, UNDER_REVIEW, INFO_REQUESTED, APPROVED, REJECTED
}
