package com.SWP391.horserace.horses.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One race a horse has been entered into, for its race history. */
@Data
@Builder
public class RaceHistoryItemResponse {
    private UUID raceId;
    private String raceCode;
    private String raceName;
    private UUID tournamentId;
    private String tournamentName;
    private OffsetDateTime scheduledStartAt;
    private String entryStatus;
    private Integer finishPosition;
    private String entryCode;
}
