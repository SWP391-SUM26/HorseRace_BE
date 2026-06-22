package com.SWP391.horserace.predictions.dto;

import com.SWP391.horserace.predictions.entity.PredictionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionRequest {

    @NotNull(message = "Race ID is required")
    private UUID raceId;

    private UUID predictedEntryId; // Nullable for exact/quinella types in some logic, but typically needed for WIN/PLACE/SHOW

    @NotNull(message = "Prediction type is required")
    private PredictionType predictionType;

    @NotNull(message = "Stake amount is required")
    @Positive(message = "Stake amount must be strictly positive")
    private BigDecimal stakeAmount;

    private String idempotencyKey;
}
