package com.SWP391.horserace.staffing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/staffing/assignments} — assign a referee to a race.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignRefereeRequest {

    @NotNull(message = "Race ID is required")
    private UUID raceId;

    @NotNull(message = "Referee user ID is required")
    private UUID refereeUserId;

    /** CHIEF | JUDGE | STEWARD | TIMEKEEPER | OBSERVER */
    private String panelRole;
}
