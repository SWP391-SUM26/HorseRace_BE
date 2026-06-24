package com.SWP391.horserace.assignments.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One ride in the logged-in jockey's schedule/history
 * ({@code GET /api/v1/assignments/me/rides}, FE-v2 jockey contract #6).
 */
@Data
@Builder
public class JockeyRideResponse {
    private UUID raceId;
    private String raceName;
    private String venue;
    private OffsetDateTime date;
    private String horseName;
    /** Finish position from race_result, null if no result recorded yet. */
    private Integer finishPosition;
    /** Prize earned for this ride's entry. */
    private BigDecimal earnings;
}
