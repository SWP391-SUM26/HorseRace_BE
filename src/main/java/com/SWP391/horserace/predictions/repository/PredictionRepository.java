package com.SWP391.horserace.predictions.repository;

import com.SWP391.horserace.predictions.entity.Prediction;
import com.SWP391.horserace.predictions.entity.PredictionStatus;
import com.SWP391.horserace.predictions.entity.PredictionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PredictionRepository extends JpaRepository<Prediction, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Prediction> {

    @Query("SELECT p FROM Prediction p LEFT JOIN FETCH p.race r LEFT JOIN FETCH p.predictedEntry pe WHERE p.spectator.userId = :userId ORDER BY p.createdAt DESC")
    List<Prediction> findBySpectatorUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId);

    @Query("SELECT p FROM Prediction p LEFT JOIN FETCH p.race r LEFT JOIN FETCH p.predictedEntry pe WHERE p.predictionId = :predictionId AND p.spectator.userId = :userId")
    Optional<Prediction> findByPredictionIdAndSpectatorUserId(@Param("predictionId") UUID predictionId, @Param("userId") UUID userId);

    boolean existsByRace_RaceIdAndSpectator_UserIdAndPredictionTypeAndPredictedEntry_EntryId(
            UUID raceId, UUID userId, PredictionType type, UUID predictedEntryId);

    boolean existsByIdempotencyKey(String idempotencyKey);

    /** Live bets of a race in a given status (e.g. PENDING) — authoritative source for live pool odds. */
    @Query("SELECT p FROM Prediction p LEFT JOIN FETCH p.predictedEntry pe WHERE p.race.raceId = :raceId AND p.status = :status")
    List<Prediction> findByRace_RaceIdAndStatus(@Param("raceId") UUID raceId, @Param("status") PredictionStatus status);

    /**
     * Active bets of one pool (race + type) in a given status — settlement recomputes the authoritative
     * pool total from these live rows (NOT {@code BettingPool.totalStake}). The spectator + predicted
     * entry are fetched so settlement can read the owner's id and the entry's scratch status without
     * lazy hits.
     */
    @Query("SELECT p FROM Prediction p "
            + "LEFT JOIN FETCH p.spectator s LEFT JOIN FETCH p.predictedEntry pe "
            + "WHERE p.race.raceId = :raceId AND p.predictionType = :type AND p.status = :status")
    List<Prediction> findByRace_RaceIdAndPredictionTypeAndStatus(
            @Param("raceId") UUID raceId,
            @Param("type") PredictionType type,
            @Param("status") PredictionStatus status);
}
