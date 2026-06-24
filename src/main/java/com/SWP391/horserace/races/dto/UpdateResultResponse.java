package com.SWP391.horserace.races.dto;

import com.SWP391.horserace.races.entity.OfficialityStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/** Body returned by PATCH /results/{resultId} — the amended row (FE-v2 mục 5). */
@Data
@Builder
public class UpdateResultResponse {
    private UUID resultId;
    private Integer finishPosition;
    private Long finishTimeMs;
    private BigDecimal lengthsBehind;
    private Integer currentVersionNo;
    private OfficialityStatus officialityStatus;
}
