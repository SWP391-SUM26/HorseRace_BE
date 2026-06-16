package com.SWP391.horserace.assignments.entity;

/** Mirrors the CHECK constraint on referee_assignment.panel_role in db/schema_v4.sql. Names MUST match DB values. */
public enum PanelRole {
    CHIEF,
    JUDGE,
    STEWARD,
    TIMEKEEPER,
    OBSERVER
}
