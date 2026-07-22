package com.SWP391.horserace.standings.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One row of a league table. Shape matches what the FE jockey leaderboard already consumes
 * ({@code rank, jockeyUserId, name, code, wins}) so the page needed no rework — the extra
 * {@code starts}/{@code earnings} fields are additive.
 */
@Data
@Builder
public class LeaderboardEntryResponse {
    private int rank;
    /** Subject id: jockey userId, horseId, or spectator userId depending on the table. */
    private UUID jockeyUserId;
    private String name;
    /** Short chip text — initials for people, horse code for horses. */
    private String code;
    private long wins;
    private long starts;
    private BigDecimal earnings;
}
