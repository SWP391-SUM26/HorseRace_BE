package com.SWP391.horserace.predictions.repository;

import com.SWP391.horserace.predictions.entity.Payout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PayoutRepository extends JpaRepository<Payout, UUID> {

    /**
     * Exactly-once guard at the prediction grain: a winning prediction already carries a Payout, so
     * a re-run of {@code settlePool} must never credit it twice (belt-and-braces alongside the
     * atomic pool-status claim + the {@code payout.prediction_id} UNIQUE constraint).
     */
    boolean existsByPrediction_PredictionId(UUID predictionId);
}
