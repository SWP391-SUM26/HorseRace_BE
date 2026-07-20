package com.SWP391.horserace.predictions.service;

import com.SWP391.horserace.predictions.dto.PoolOddsResponse;
import com.SWP391.horserace.predictions.dto.PredictionRequest;
import com.SWP391.horserace.predictions.dto.PredictionResponse;

import java.util.List;
import java.util.UUID;

public interface PredictionService {

    PredictionResponse submitPrediction(UUID userId, PredictionRequest request);

    List<PredictionResponse> getPredictionHistory(UUID userId);

    PredictionResponse getPredictionDetail(UUID userId, UUID predictionId);

    void cancelPrediction(UUID userId, UUID predictionId);

    /** Live, estimated pari-mutuel odds per runner for every pool of a race (summed from live bets). */
    List<PoolOddsResponse> getLivePoolOdds(UUID raceId);
}
