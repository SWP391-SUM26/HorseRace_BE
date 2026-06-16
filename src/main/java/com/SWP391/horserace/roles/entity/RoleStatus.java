package com.SWP391.horserace.roles.entity;

/**
 * Mirrors the CHECK constraint on role.status in db/schema_v4.sql.
 * Stored as a string via @Enumerated(EnumType.STRING) — names MUST match the DB values.
 */
public enum RoleStatus {
    ACTIVE,
    INACTIVE
}
