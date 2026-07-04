package com.SWP391.horserace.owner.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One of the owner's race-targeted registrations, annotated with whether it actually entered the
 * race and its result. Powers the owner's per-race report (registered vs. participated).
 */
@Data
@Builder
public class OwnerRaceReportRow {

    // -- race --
    private UUID raceId;
    private String raceCode;
    private String raceName;
    private String raceStatus;
    private OffsetDateTime scheduledStartAt;
    private String tournamentName;

    // -- horse --
    private UUID horseId;
    private String horseName;

    // -- registration --
    private UUID registrationId;
    private String registrationCode;
    private String registrationStatus;
    private String rejectionReason;

    // -- participation --
    /** True when the registration became a race entry (the horse actually entered the race). */
    private boolean entered;
    /** Per the owner-report rule: a registration that never became an entry "did not participate". */
    private boolean participated;
    private String entryStatus;   // ENTERED / CHECKED_IN / SCRATCHED / DISQUALIFIED / FINISHED (null if no entry)
    private Integer entryNo;

    // -- result (if any) --
    private Integer finishPosition;
    private Long finishTimeMs;
}
