package com.SWP391.horserace.predictions.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** One prediction as the admin moderation table sees it. */
@Data
@Builder
public class AdminPredictionResponse {
    private UUID predictionId;
    private UUID raceId;
    private String raceCode;
    private String raceName;
    private UUID spectatorUserId;
    private String spectatorName;
    private String spectatorEmail;
    private String predictionType;
    private String horseName;
    private BigDecimal stakeAmount;
    private String status;
    private BigDecimal payoutAmount;
    private OffsetDateTime submittedAt;
    private OffsetDateTime settledAt;
}
