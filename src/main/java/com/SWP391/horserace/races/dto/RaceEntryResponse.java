package com.SWP391.horserace.races.dto;

import com.SWP391.horserace.races.entity.RaceEntryStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class RaceEntryResponse {
    private UUID entryId;
    private String entryCode;
    private Integer entryNo;
    private Integer laneNo;
    private String drawStall;
    private RaceEntryStatus status;
    private UUID raceId;
    private UUID registrationId;
    private UUID horseId;
    private String horseName;
    private UUID ownerUserId;
    private String ownerName;
    private UUID jockeyUserId;
    private String jockeyName;
    private Integer weightCarriedLbs;
    private String recentForm;
    private String odds;
    private OffsetDateTime createdAt;
}
