package com.SWP391.horserace.races.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Body for POST /api/v1/races/{raceId}/results — bulk upsert the finish order (FE-v2 mục 5). */
public record RecordResultsRequest(
        @Valid @NotNull List<ResultRow> results,
        /** The referee's per-race code (admin-issued); required for referees, ignored for admins. */
        String refCode
) {

    /** One entry's recorded result. */
    public record ResultRow(
            @NotNull UUID entryId,
            Integer finishPosition,
            Long finishTimeMs,
            BigDecimal lengthsBehind,
            BigDecimal score
    ) {
    }
}
