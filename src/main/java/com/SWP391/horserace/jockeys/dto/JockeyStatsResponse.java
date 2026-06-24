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
    /** Career win tally from {@code jockey_profile.win_count}. */
    private int careerWins;
    /** Sum of prize earned over the caller's rides (no season dimension yet — equals careerEarnings). */
    private BigDecimal seasonEarnings;
    /** Sum of prize earned over the caller's rides. */
    private BigDecimal careerEarnings;
}
