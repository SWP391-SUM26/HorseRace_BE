package com.SWP391.horserace.violations.entity;

/**
 * Mirrors the CHECK constraint on race_violation.infraction_type in db/schema_v4.sql.
 * Stored as a string via @Enumerated(EnumType.STRING) — names MUST match the DB values.
 */
public enum InfractionType {
    BUMPING,
    INTERFERENCE,
    WHIP_USAGE,
    CROWDING,
    OTHER
}
