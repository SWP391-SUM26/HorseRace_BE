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
    /**
     * What the JOCKEY took home for this ride — the prize row credited to them, zero until the
     * race is certified. Previously this carried the horse's whole prize, so a rider on a 10%
     * share saw ten times what actually reached their wallet.
     */
    private BigDecimal earnings;
    /** What the HORSE won in this race, shown beside {@link #earnings} to make the split legible. */
    private BigDecimal horsePrize;
    /** The share agreed on this invitation, so the rider can check the arithmetic themselves. */
    private BigDecimal agreedPrizePercent;
}
