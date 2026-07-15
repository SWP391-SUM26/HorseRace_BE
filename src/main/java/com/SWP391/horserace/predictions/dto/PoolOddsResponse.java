package com.SWP391.horserace.predictions.dto;

import com.SWP391.horserace.predictions.entity.PredictionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Live, ESTIMATED pari-mutuel odds for one (race, prediction_type) pool. Odds are computed from the
 * SUM of live (PENDING, non-scratched) {@code Prediction} stakes — the same authoritative source
 * settlement uses — NOT the drift-prone {@code betting_pool.total_stake} display counter. Values move
 * as more money enters the pool, so they are only an estimate until the pool closes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PoolOddsResponse {

    private UUID poolId;
    private PredictionType predictionType;
    private BigDecimal rakePercent;
    /** Σ of live stakes across all non-scratched runners in this pool. */
    private BigDecimal totalLiveStake;
    private List<RunnerOdds> runners;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RunnerOdds {
        private UUID entryId;
        private BigDecimal stakeOnRunner;
        /** estimated_payout_per_unit = Σ(live stakes) * (1 - rake) / stakeOnRunner; null if no stake. */
        private BigDecimal estimatedPayoutPerUnit;
    }
}
