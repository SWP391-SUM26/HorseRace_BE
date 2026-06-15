package com.SWP391.horserace.assignments.entity;

/** Mirrors the CHECK constraint on jockey_assignment.status in db/schema_v4.sql. Names MUST match DB values. */
public enum JockeyAssignmentStatus {
    INVITED,
    ACCEPTED,
    DECLINED,
    CANCELLED
}
