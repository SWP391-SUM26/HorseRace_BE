package com.SWP391.horserace.penalties.entity;

/**
 * Mirrors the CHECK constraint on penalty.penalty_type in db/schema_v4.sql.
 * Stored as a string via @Enumerated(EnumType.STRING) — names MUST match the DB values.
 */
public enum PenaltyType {
    WARNING,
    TIME_PENALTY,
    FINE,
    DISQUALIFICATION,
    SUSPENSION
}
