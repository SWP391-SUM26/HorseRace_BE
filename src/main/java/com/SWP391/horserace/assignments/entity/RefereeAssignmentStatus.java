package com.SWP391.horserace.assignments.entity;

import java.util.Set;

/** Mirrors the CHECK constraint on referee_assignment.status in db/schema_v4.sql. Names MUST match DB values. */
public enum RefereeAssignmentStatus {
    ASSIGNED,
    CONFIRMED,
    DECLINED,
    REVOKED;

    /**
     * Statuses under which a referee may officiate a race — request submission codes, review entry
     * documents, submit/certify results, file violations, drive the live race. Being assigned is
     * enough; accepting the race (CONFIRMED) is NOT required. A DECLINED (refused) or REVOKED
     * (removed by admin) assignment does not qualify.
     */
    public static final Set<RefereeAssignmentStatus> OFFICIATING = Set.of(ASSIGNED, CONFIRMED);
}
