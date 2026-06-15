package com.SWP391.horserace.predictions.entity;

/**
 * Mirrors the CHECK constraint on payout.status in db/schema_v4.sql.
 * Names MUST match the DB values.
 */
public enum PayoutStatus {
    PENDING,
    PAID,
    FAILED,
    CANCELLED
}
