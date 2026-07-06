package com.SWP391.horserace.inspections.entity;

/**
 * Mirrors the CHECK constraint on entry_document_review.document_status in db/schema_v4.sql.
 * Stored as a string via @Enumerated(EnumType.STRING) — names MUST match the DB values.
 */
public enum DocumentReviewStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}
