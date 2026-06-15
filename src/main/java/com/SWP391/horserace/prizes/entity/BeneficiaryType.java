package com.SWP391.horserace.prizes.entity;

/**
 * Mirrors the CHECK constraint on prize.beneficiary_type in db/schema_v4.sql.
 * Names MUST match the DB values.
 */
public enum BeneficiaryType {
    OWNER,
    JOCKEY,
    HORSE,
    TEAM
}
