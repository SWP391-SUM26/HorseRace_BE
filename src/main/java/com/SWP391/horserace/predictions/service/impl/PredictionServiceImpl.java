package com.SWP391.horserace.predictions.service.impl;

import com.SWP391.horserace.predictions.dto.PredictionRequest;
import com.SWP391.horserace.predictions.dto.PredictionResponse;
import com.SWP391.horserace.predictions.entity.BettingPool;
import com.SWP391.horserace.predictions.entity.BettingPoolStatus;
import com.SWP391.horserace.predictions.entity.Prediction;
import com.SWP391.horserace.predictions.entity.PredictionStatus;
import com.SWP391.horserace.predictions.repository.BettingPoolRepository;
import com.SWP391.horserace.predictions.repository.PredictionRepository;
import com.SWP391.horserace.predictions.service.PredictionService;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.races.entity.RaceStatus;
import com.SWP391.horserace.races.repository.RaceEntryRepository;
import com.SWP391.horserace.races.repository.RaceRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PredictionServiceImpl implements PredictionService {

    private final PredictionRepository predictionRepository;
    private final BettingPoolRepository bettingPoolRepository;
    private final RaceRepository raceRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public PredictionResponse submitPrediction(UUID userId, PredictionRequest request) {
        // 1. Check Idempotency Key
        if (request.getIdempotencyKey() != null && predictionRepository.existsByIdempotencyKey(request.getIdempotencyKey())) {
            throw new AppException(ErrorCode.IDEMPOTENCY_KEY_EXISTED);
        }

        // 2. Validate Race
        Race race = raceRepository.findByRaceIdAndDeletedFalse(request.getRaceId())
                .orElseThrow(() -> new AppException(ErrorCode.RACE_NOT_FOUND));

        if (race.getStatus() != RaceStatus.SCHEDULED && race.getStatus() != RaceStatus.OPEN) {
            throw new AppException(ErrorCode.PREDICTION_RACE_NOT_OPEN);
        }

        if (race.getPredictionCutoffAt() != null && OffsetDateTime.now().isAfter(race.getPredictionCutoffAt())) {
            throw new AppException(ErrorCode.PREDICTION_RACE_NOT_OPEN);
        }

        // 3. Validate Entry (if provided/required)
        RaceEntry predictedEntry = null;
        if (request.getPredictedEntryId() != null) {
            predictedEntry = raceEntryRepository.findById(request.getPredictedEntryId())
                    .orElseThrow(() -> new AppException(ErrorCode.PREDICTION_ENTRY_NOT_FOUND));

            if (!predictedEntry.getRace().getRaceId().equals(race.getRaceId())) {
                throw new AppException(ErrorCode.PREDICTION_ENTRY_MISMATCH);
            }
        }

        // 4. Check Uniqueness constraint (race_id, spectator_user_id, prediction_type, predicted_entry_id)
        boolean alreadyPredicted = predictionRepository.existsByRace_RaceIdAndSpectator_UserIdAndPredictionTypeAndPredictedEntry_EntryId(
                race.getRaceId(), userId, request.getPredictionType(), request.getPredictedEntryId()
        );
        if (alreadyPredicted) {
            throw new AppException(ErrorCode.PREDICTION_ALREADY_EXISTS);
        }

        // 5. Get or Create Betting Pool
        BettingPool pool = bettingPoolRepository.findByRace_RaceIdAndPredictionType(race.getRaceId(), request.getPredictionType())
                .orElseGet(() -> {
                    BettingPool newPool = BettingPool.builder()
                            .race(race)
                            .predictionType(request.getPredictionType())
                            .status(BettingPoolStatus.OPEN)
                            .build();
                    return bettingPoolRepository.save(newPool);
                });

        if (pool.getStatus() != BettingPoolStatus.OPEN) {
            throw new AppException(ErrorCode.BETTING_POOL_CLOSED);
        }

        // Add stake to pool
        pool.setTotalStake(pool.getTotalStake().add(request.getStakeAmount()));
        bettingPoolRepository.save(pool);

        // 6. Get User Reference
        User spectator = userRepository.getReferenceById(userId);

        // 7. Create Prediction
        Prediction prediction = Prediction.builder()
                .race(race)
                .spectator(spectator)
                .predictedEntry(predictedEntry)
                .predictionType(request.getPredictionType())
                .stakeAmount(request.getStakeAmount())
                .idempotencyKey(request.getIdempotencyKey())
                .status(PredictionStatus.PENDING)
                .submittedAt(OffsetDateTime.now())
                .build();

        prediction = predictionRepository.save(prediction);

        return mapToResponse(prediction);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PredictionResponse> getPredictionHistory(UUID userId) {
        return predictionRepository.findBySpectatorUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PredictionResponse getPredictionDetail(UUID userId, UUID predictionId) {
        Prediction prediction = predictionRepository.findByPredictionIdAndSpectatorUserId(predictionId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.PREDICTION_NOT_FOUND));
        return mapToResponse(prediction);
    }

    @Override
    @Transactional
    public void cancelPrediction(UUID userId, UUID predictionId) {
        Prediction prediction = predictionRepository.findByPredictionIdAndSpectatorUserId(predictionId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.PREDICTION_NOT_FOUND));

        if (prediction.getStatus() != PredictionStatus.PENDING) {
            throw new AppException(ErrorCode.PREDICTION_CANNOT_CANCEL);
        }

        Race race = prediction.getRace();
        if (race.getStatus() != RaceStatus.SCHEDULED && race.getStatus() != RaceStatus.OPEN) {
            throw new AppException(ErrorCode.PREDICTION_CANNOT_CANCEL);
        }

        if (race.getPredictionCutoffAt() != null && OffsetDateTime.now().isAfter(race.getPredictionCutoffAt())) {
            throw new AppException(ErrorCode.PREDICTION_CANNOT_CANCEL);
        }

        // Subtract stake from pool
        bettingPoolRepository.findByRace_RaceIdAndPredictionType(race.getRaceId(), prediction.getPredictionType())
                .ifPresent(pool -> {
                    if (pool.getStatus() == BettingPoolStatus.OPEN) {
                        pool.setTotalStake(pool.getTotalStake().subtract(prediction.getStakeAmount()));
                        bettingPoolRepository.save(pool);
                    }
                });

        prediction.setStatus(PredictionStatus.VOID);
        predictionRepository.save(prediction);
    }

    private PredictionResponse mapToResponse(Prediction prediction) {
        return PredictionResponse.builder()
                .predictionId(prediction.getPredictionId())
                .raceId(prediction.getRace() != null ? prediction.getRace().getRaceId() : null)
                .raceCode(prediction.getRace() != null ? prediction.getRace().getRaceCode() : null)
                .raceName(prediction.getRace() != null ? prediction.getRace().getName() : null)
                .spectatorUserId(prediction.getSpectator() != null ? prediction.getSpectator().getUserId() : null)
                .predictedEntryId(prediction.getPredictedEntry() != null ? prediction.getPredictedEntry().getEntryId() : null)
                .predictionType(prediction.getPredictionType())
                .lockedOdds(prediction.getLockedOdds())
                .stakeAmount(prediction.getStakeAmount())
                .potentialPayout(prediction.getPotentialPayout())
                .status(prediction.getStatus())
                .submittedAt(prediction.getSubmittedAt())
                .settledAt(prediction.getSettledAt())
                .idempotencyKey(prediction.getIdempotencyKey())
                .createdAt(prediction.getCreatedAt())
                .build();
    }
}
