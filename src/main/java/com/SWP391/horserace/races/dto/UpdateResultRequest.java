package com.SWP391.horserace.races.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** Body for PATCH /api/v1/races/{raceId}/results/{resultId} — inline edit one row (FE-v2 mục 5). */
public record UpdateResultRequest(
        @Positive(message = "Finish position must be positive") Integer finishPosition,
        @Min(value = 0, message = "Finish time cannot be negative") Long finishTimeMs,
        BigDecimal lengthsBehind,
        String changeReason
) {
}
