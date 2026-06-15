package com.SWP391.horserace.prizes.entity;

/**
 * Mirrors the CHECK constraint on prize.status in db/schema_v4.sql.
 * Names MUST match the DB values.
 */
public enum PrizeStatus {
    DRAFT,
    ANNOUNCED,
    AWARDED,
    PAID,
    CANCELLED
}
