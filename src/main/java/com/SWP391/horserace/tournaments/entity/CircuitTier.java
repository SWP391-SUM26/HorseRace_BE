package com.SWP391.horserace.tournaments.entity;

/**
 * Mirrors the CHECK constraint on tournament.circuit_tier in db/schema_v4.sql.
 * Stored as a string via @Enumerated(EnumType.STRING) — names MUST match the DB values.
 * Nullable: a tournament may have no graded circuit tier.
 */
public enum CircuitTier {
    GROUP_1,
    GROUP_2,
    GROUP_3,
    LISTED
}
