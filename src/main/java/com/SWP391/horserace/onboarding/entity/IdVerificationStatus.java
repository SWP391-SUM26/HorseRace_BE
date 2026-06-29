package com.SWP391.horserace.onboarding.entity;

/** Identity document verification outcome. Matches DB CHECK on id_verification_status. */
public enum IdVerificationStatus {
    VALID, PENDING, FAILED
}
