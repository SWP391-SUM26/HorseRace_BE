package com.SWP391.horserace.predictions.repository;

import com.SWP391.horserace.predictions.entity.Prediction;
import com.SWP391.horserace.predictions.entity.PredictionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PredictionRepository extends JpaRepository<Prediction, UUID> {

    @Query("SELECT p FROM Prediction p LEFT JOIN FETCH p.race r LEFT JOIN FETCH p.predictedEntry pe WHERE p.spectator.userId = :userId ORDER BY p.createdAt DESC")
    List<Prediction> findBySpectatorUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId);

    @Query("SELECT p FROM Prediction p LEFT JOIN FETCH p.race r LEFT JOIN FETCH p.predictedEntry pe WHERE p.predictionId = :predictionId AND p.spectator.userId = :userId")
    Optional<Prediction> findByPredictionIdAndSpectatorUserId(@Param("predictionId") UUID predictionId, @Param("userId") UUID userId);

    boolean existsByRace_RaceIdAndSpectator_UserIdAndPredictionTypeAndPredictedEntry_EntryId(
            UUID raceId, UUID userId, PredictionType type, UUID predictedEntryId);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
