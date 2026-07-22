package com.SWP391.horserace.jockeys.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Aggregated performance + earnings stats for the logged-in jockey
 * ({@code GET /api/v1/jockeys/me/stats}, FE-v2 jockey contract #1).
 *
 * <p>Computed over the caller's ACCEPTED rides that have a recorded result.
 */
@Data
@Builder
public class JockeyStatsResponse {
    /** wins / totalRides * 100, rounded to 1 decimal (0 when no rides). */
    private double winRate;
    /** Number of rides that have a recorded result. */
    private int totalRides;
    /** Rides finished 1st. */
    private int wins;
    /** Rides finished 2nd. */
    private int places;
    /** top3 / totalRides * 100, rounded to 1 decimal (0 when no rides). */
    private double top3Rate;
    /** Average finish position, rounded to 1 decimal (0 when no rides). */
    private double avgPlacement;
    /** Career win tally, computed from official results (not the never-written win_count column). */
    private int careerWins;
    /** The jockey's OWN take (no season dimension yet — equals careerEarnings). */
    private BigDecimal seasonEarnings;
    /** The jockey's OWN take: sum of prize rows credited to them, i.e. what reached their wallet. */
    private BigDecimal careerEarnings;
    /**
     * Total prize won by the HORSES this jockey rode. Shown next to {@code careerEarnings} so the
     * agreed share is legible instead of looking like a missing payment — these two were previously
     * conflated, and the horse's figure was reported as the rider's income.
     */
    private BigDecimal horseEarnings;
}
