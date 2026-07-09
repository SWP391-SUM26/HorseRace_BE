package com.SWP391.horserace.races.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Body for PATCH /races/{raceId}/entries/{entryId}/reject — the mandatory rejection reason (FR-11). */
@Data
public class RejectEntryRequest {
    /** Human-readable reason the entry's documents were rejected (required, non-blank). */
    @NotBlank(message = "A rejection reason is required")
    private String reason;
}
