package com.SWP391.horserace.races.entity;

/**
 * Mirrors the CHECK constraint on race.status in db/schema_v4.sql.
 * Stored as a string via @Enumerated(EnumType.STRING) — names MUST match the DB values.
 *
 * <p>§D4 — CANONICAL BE RACE STATUS SET (do not add new states; the FE maps these):
 * {@code SCHEDULED → OPEN → CLOSED → RUNNING → FINISHED → OFFICIAL}, plus {@code CANCELLED}.
 * FE Figma vocabulary maps onto this set, e.g. DRAFT/PENDING_ENTRIES → SCHEDULED,
 * CONFIRMED → OPEN/CLOSED, ACTIVE → RUNNING. The /races/stats KPI buckets are:
 * scheduled=SCHEDULED, active=OPEN, cancelled=CANCELLED, total=all non-deleted.
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
