package com.SWP391.horserace.predictions.repository;

import com.SWP391.horserace.predictions.entity.BettingPool;
import com.SWP391.horserace.predictions.entity.PredictionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BettingPoolRepository extends JpaRepository<BettingPool, UUID> {
    Optional<BettingPool> findByRace_RaceIdAndPredictionType(UUID raceId, PredictionType predictionType);
}
