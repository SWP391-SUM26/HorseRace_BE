package com.SWP391.horserace.predictions.dto;

import com.SWP391.horserace.predictions.entity.PredictionStatus;
import com.SWP391.horserace.predictions.entity.PredictionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionResponse {
    private UUID predictionId;
    private UUID raceId;
    private String raceCode;
    private String raceName;
    private UUID spectatorUserId;
    private UUID predictedEntryId;
    private PredictionType predictionType;
    private BigDecimal lockedOdds;
    private BigDecimal stakeAmount;
    private BigDecimal potentialPayout;
    private PredictionStatus status;
    private OffsetDateTime submittedAt;
    private OffsetDateTime settledAt;
    private String idempotencyKey;
    private OffsetDateTime createdAt;
}
