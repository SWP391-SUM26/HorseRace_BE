package com.SWP391.horserace.reports.entity;

/**
 * Mirrors the CHECK constraint on referee_report.report_type in db/schema_v4.sql.
 * Stored as a string via @Enumerated(EnumType.STRING) — names MUST match the DB values.
 */
public enum ReportType {
    INCIDENT,
    VIOLATION,
    OBJECTION,
    GENERAL
}
