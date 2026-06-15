package com.SWP391.horserace.tournaments.entity;

/**
 * Mirrors the CHECK constraint on tournament_round.status in db/schema_v4.sql.
 * Stored as a string via @Enumerated(EnumType.STRING) — names MUST match the DB values.
 */
public enum RoundStatus {
    PLANNED,
    ONGOING,
    COMPLETED,
    CANCELLED
}
