package com.SWP391.horserace.horses.entity;

/**
 * Mirrors the CHECK constraint on horse.health_status in db/schema_v4.sql.
 * Stored as a string via @Enumerated(EnumType.STRING) — names MUST match the DB values.
 */
public enum HorseHealthStatus {
    HEALTHY,
    INJURED,
    QUARANTINE,
    UNFIT
}
