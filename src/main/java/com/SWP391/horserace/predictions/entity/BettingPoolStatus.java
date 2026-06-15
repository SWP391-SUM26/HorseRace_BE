package com.SWP391.horserace.predictions.entity;

/**
 * Mirrors the CHECK constraint on betting_pool.status in db/schema_v4.sql.
 * Names MUST match the DB values.
 */
public enum BettingPoolStatus {
    OPEN,
    CLOSED,
    SETTLED
}
