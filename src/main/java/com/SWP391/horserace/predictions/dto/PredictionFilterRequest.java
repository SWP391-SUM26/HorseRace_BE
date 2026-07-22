package com.SWP391.horserace.predictions.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Query params for {@code GET /api/v1/admin/predictions}. Every field is optional. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionFilterRequest {

    /** Free text over the bettor's name/email and the race name/code. */
    private String q;
    private String status;
    private String predictionType;
    private UUID raceId;
    private UUID spectatorUserId;

    @Builder.Default
    private Integer page = 0;
    @Builder.Default
    private Integer size = 10;
    @Builder.Default
    private String sortBy = "submittedAt";
    @Builder.Default
    private String sortDir = "desc";
}
