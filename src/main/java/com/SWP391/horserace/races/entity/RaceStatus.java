package com.SWP391.horserace.races.entity;

/**
 * Mirrors the CHECK constraint on race.status in db/schema_v4.sql.
 * Stored as a string via @Enumerated(EnumType.STRING) — names MUST match the DB values.
 */
public enum RaceStatus {
    SCHEDULED,
    OPEN,
    CLOSED,
    RUNNING,
    FINISHED,
    OFFICIAL,
    CANCELLED
}
