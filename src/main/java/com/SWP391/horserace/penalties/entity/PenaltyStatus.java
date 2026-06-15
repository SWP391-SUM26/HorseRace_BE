package com.SWP391.horserace.penalties.entity;

/**
 * Mirrors the CHECK constraint on penalty.status in db/schema_v4.sql.
 * Stored as a string via @Enumerated(EnumType.STRING) — names MUST match the DB values.
 */
public enum PenaltyStatus {
    ISSUED,
    UPHELD,
    OVERTURNED,
    CANCELLED
}
