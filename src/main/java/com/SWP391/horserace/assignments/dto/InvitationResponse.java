package com.SWP391.horserace.assignments.dto;

import com.SWP391.horserace.assignments.entity.JockeyAssignmentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * API view of a jockey assignment (invitation). Combines data from
 * {@code jockey_assignment}, {@code race_entry}, {@code tournament_registration},
 * {@code horse}, {@code race}, {@code tournament}, and {@code app_user} (jockey + owner)
 * so the client gets a rich, denormalized response in one call.
 */
@Data
@Builder
public class InvitationResponse {

    // -- assignment info --
    private UUID assignmentId;
    private JockeyAssignmentStatus status;   // INVITED, ACCEPTED, DECLINED, CANCELLED
    private OffsetDateTime invitedAt;
    private OffsetDateTime respondedAt;
    private OffsetDateTime createdAt;

    // -- horse info (entry → registration → horse) --
    private UUID horseId;
    private String horseName;
    private String horseCode;

    // -- race info (entry → race) --
    private UUID raceId;
    private String raceName;
    private String raceCode;
    private OffsetDateTime scheduledStartAt;
    private String trackCondition;
    private Integer distanceMeter;

    // -- tournament info (race → tournament) --
    private UUID tournamentId;
    private String tournamentName;
    private String tournamentLocation;

    // -- jockey info --
    private UUID jockeyUserId;
    private String jockeyName;
    private String jockeyAvatarUrl;

    // -- owner info (entry → registration → owner) --
    private UUID ownerUserId;
    private String ownerName;

    // -- entry info --
    private UUID entryId;
    private String entryCode;
    private Integer entryNo;
}
