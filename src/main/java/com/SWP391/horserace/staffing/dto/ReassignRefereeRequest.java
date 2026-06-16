package com.SWP391.horserace.staffing.dto;

import com.SWP391.horserace.assignments.entity.PanelRole;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request body for {@code PUT /api/v1/staffing/assignments/{id}} — reassign a different referee.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReassignRefereeRequest {

    @NotNull(message = "New referee user ID is required")
    private UUID newRefereeUserId;

    /** CHIEF | JUDGE | STEWARD | TIMEKEEPER | OBSERVER (optional, keeps current if null). */
    private PanelRole panelRole;
}
