package com.SWP391.horserace.reports.entity;

/**
 * Mirrors the CHECK constraint on referee_report.severity_level in db/schema_v4.sql.
 * Stored as a string via @Enumerated(EnumType.STRING) — names MUST match the DB values.
 */
public enum SeverityLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
