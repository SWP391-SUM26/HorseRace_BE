package com.SWP391.horserace.races.dto;

import com.SWP391.horserace.races.entity.OfficialityStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Body for GET /api/v1/races/{raceId}/results — full result sheet for a race (FE-v2 mục 5). */
@Data
@Builder
public class RaceResultsResponse {
    private UUID raceId;
    private OfficialityStatus officialityStatus;
    private Long winningTimeMs;
    private String trackCondition;
    private String trackBias;
    private BigDecimal windSpeedKph;
    private List<String> fractions;
    private String photofinishUrl;
    private List<OrderRow> order;

    /** One row of the finish order. */
    @Data
    @Builder
    public static class OrderRow {
        private UUID resultId;
        private Integer finishPosition;
        private Integer entryNo;
        private String horseName;
        private String jockeyName;
        private Integer weightCarriedLbs;
        private Long finishTimeMs;
        private BigDecimal lengthsBehind;
        private String odds;
    }
}
