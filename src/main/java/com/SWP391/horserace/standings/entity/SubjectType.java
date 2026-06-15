package com.SWP391.horserace.standings.entity;

/**
 * Mirrors the CHECK constraint on standing.subject_type in db/schema_v4.sql.
 * Stored as a string via @Enumerated(EnumType.STRING) — names MUST match the DB values.
 */
public enum SubjectType {
    HORSE,
    JOCKEY
}
