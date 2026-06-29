package com.SWP391.horserace.users.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A first-place finish of a horse owned by a user (admin user-detail "wins"). */
@Data
@Builder
public class UserWinResponse {
    private UUID raceId;
    private String raceCode;
    private String raceName;
    private UUID tournamentId;
    private String tournamentName;
    private UUID horseId;
    private String horseName;
    private Integer finishPosition;
    private OffsetDateTime scheduledStartAt;
    private BigDecimal prizeEarned;
}
