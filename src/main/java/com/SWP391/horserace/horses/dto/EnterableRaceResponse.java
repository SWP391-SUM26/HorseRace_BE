package com.SWP391.horserace.horses.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A race a given horse can still be entered into (Enter Race modal):
 * an APPROVED registration exists for the horse in the race's tournament,
 * the race is open (SCHEDULED/OPEN), and the horse is not already entered.
 */
@Data
@Builder
public class EnterableRaceResponse {
    private UUID raceId;
    private String raceCode;
    private String name;
    private UUID tournamentId;
    private String tournamentName;
    private OffsetDateTime scheduledStartAt;
    private String status;
}
