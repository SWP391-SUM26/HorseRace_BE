package com.SWP391.horserace.staffing.dto;

import com.SWP391.horserace.assignments.entity.PanelRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/** Body for POST /staffing/tournament-assignments. */
@Data
public class InviteTournamentRefereeRequest {

    @NotNull(message = "Tournament ID is required")
    private UUID tournamentId;

    @NotNull(message = "Referee user ID is required")
    private UUID refereeUserId;

    /** CHIEF | JUDGE | STEWARD | TIMEKEEPER | OBSERVER (optional). */
    private PanelRole panelRole;
}
