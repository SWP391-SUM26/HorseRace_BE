package com.SWP391.horserace.races.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Response for POST /api/v1/races/{raceId}/report — the published rows + created violation ids. */
@Data
@Builder
public class SubmitReportResponse {

    private UUID raceId;

    /** The upserted result rows (finish order). */
    private List<ResultRowResponse> results;

    /** Ids of the violations created in this submit. */
    private List<UUID> violationIds;

    /** When the report was locked in (the referee-submitted timestamp). */
    private OffsetDateTime submittedAt;
}
