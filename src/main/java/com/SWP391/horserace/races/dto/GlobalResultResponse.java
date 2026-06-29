package com.SWP391.horserace.races.dto;

import com.SWP391.horserace.races.entity.OfficialityStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** One row of the global management result list. */
@Data
@Builder
public class GlobalResultResponse {
    private UUID resultId;
    private UUID raceId;
    private String raceName;
    private String raceCode;
    
    private UUID horseId;
    private String horseName;
    
    private UUID jockeyId;
    private String jockeyName;
    
    private Integer finishPosition;
    private Long finishTimeMs;
    private BigDecimal lengthsBehind;
    private BigDecimal score;
    private OfficialityStatus officialityStatus;
    
    private OffsetDateTime raceScheduledStartAt;
}
