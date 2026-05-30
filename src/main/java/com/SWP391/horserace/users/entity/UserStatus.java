package com.SWP391.horserace.users.entity;

/**
 * Mirrors the CHECK constraint on app_user.status in db/schema_v2.sql.
 * Stored as a string via @Enumerated(EnumType.STRING) — names MUST match the DB values.
 */
public enum UserStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED,
    BANNED
}
