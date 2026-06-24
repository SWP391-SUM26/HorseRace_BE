package com.SWP391.horserace.races.dto;

import com.SWP391.horserace.races.entity.RaceStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class RaceResponse {
    private UUID raceId;
    private String raceCode;
    private String name;
    private String raceType;
    private Integer distanceMeter;
    private String trackCondition;
    private String weatherCondition;
    private OffsetDateTime scheduledStartAt;
    private OffsetDateTime actualStartAt;
    private OffsetDateTime actualEndAt;
    private OffsetDateTime predictionCutoffAt;
    private Integer maxParticipants;
    private String venue;
    // §D1 — linked structured venue (FK). venueName is populated from the linked venue when present.
    private UUID venueId;
    private String venueName;
    // §D2 — number of race_entry rows for this race (avoids a second /entries call)
    private long entriesCount;
    private Integer goingMoisturePct;
    private BigDecimal totalPurse;
    private List<PrizeDistributionDto> prizeDistribution;
    private RaceStatus status;
    private UUID tournamentId;
    private String tournamentName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
