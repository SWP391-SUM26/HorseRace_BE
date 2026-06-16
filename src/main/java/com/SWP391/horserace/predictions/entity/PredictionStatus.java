package com.SWP391.horserace.predictions.entity;

/**
 * Mirrors the CHECK constraint on prediction.status in db/schema_v4.sql.
 * Names MUST match the DB values.
 */
public enum PredictionStatus {
    PENDING,
    CONFIRMED,
    WON,
    LOST,
    VOID,
    REFUNDED
}
