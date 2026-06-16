package com.SWP391.horserace.registrations.entity;

/**
 * Mirrors the CHECK constraint on tournament_registration.status in db/schema_v4.sql.
 * Stored as a string via @Enumerated(EnumType.STRING) — names MUST match the DB values.
 */
public enum RegistrationStatus {
    DRAFT,
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    WITHDRAWN
}
