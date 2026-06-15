package com.SWP391.horserace.attachments.entity;

/** Mirrors the CHECK constraint on attachment.sensitivity_level in db/schema_v4.sql. Names MUST match DB values. */
public enum SensitivityLevel {
    PUBLIC,
    INTERNAL,
    CONFIDENTIAL,
    RESTRICTED
}
