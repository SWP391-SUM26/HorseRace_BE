package com.SWP391.horserace.races.dto;

import com.SWP391.horserace.races.entity.OfficialityStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/** One row of the POST /results response — a recorded result for an entry (FE-v2 mục 5). */
@Data
@Builder
public class ResultRowResponse {
    private UUID resultId;
    private UUID entryId;
    private Integer entryNo;
    private String horseName;
    private String jockeyName;
    private Integer finishPosition;
    private Long finishTimeMs;
    private BigDecimal lengthsBehind;
    private BigDecimal score;
    private OfficialityStatus officialityStatus;
}
