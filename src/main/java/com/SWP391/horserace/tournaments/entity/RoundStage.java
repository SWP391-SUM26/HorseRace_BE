package com.SWP391.horserace.tournaments.entity;

/**
 * Mirrors the CHECK constraint on tournament_round.stage in db/schema_v4.sql.
 * Stored as a string via @Enumerated(EnumType.STRING) — names MUST match the DB values.
 */
public enum RoundStage {
    QUALIFIER,
    HEAT,
    SEMI,
    FINAL
}
