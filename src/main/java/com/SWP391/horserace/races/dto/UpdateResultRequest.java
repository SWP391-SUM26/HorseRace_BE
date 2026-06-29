package com.SWP391.horserace.races.dto;

import java.math.BigDecimal;

/** Body for PATCH /api/v1/races/{raceId}/results/{resultId} — inline edit one row (FE-v2 mục 5). */
public record UpdateResultRequest(
        Integer finishPosition,
        Long finishTimeMs,
        BigDecimal lengthsBehind,
        String changeReason
) {
}
