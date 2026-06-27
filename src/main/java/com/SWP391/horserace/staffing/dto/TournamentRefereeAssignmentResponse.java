package com.SWP391.horserace.staffing.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A tournament-level referee invitation (admin queue + referee inbox). */
@Data
@Builder
public class TournamentRefereeAssignmentResponse {
    private UUID id;
    private UUID tournamentId;
    private String tournamentName;
    private UUID refereeUserId;
    private String refereeName;
    private String refereeAvatarUrl;
    private String panelRole;
    private String status;
    private OffsetDateTime invitedAt;
    private OffsetDateTime respondedAt;
}
