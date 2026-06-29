package com.SWP391.horserace.assignments.entity;

/** Lifecycle of a tournament-level referee invitation. Mirrors the
 *  tournament_referee_assignment.status CHECK in schema_v4.sql. */
public enum TournamentRefereeStatus {
    INVITED,
    ACCEPTED,
    DECLINED,
    REVOKED
}
