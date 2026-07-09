package com.SWP391.horserace.predictions.service.impl;

import com.SWP391.horserace.predictions.dto.PoolOddsResponse;
import com.SWP391.horserace.predictions.dto.PredictionRequest;
import com.SWP391.horserace.predictions.dto.PredictionResponse;
import com.SWP391.horserace.predictions.entity.BettingPool;
import com.SWP391.horserace.predictions.entity.BettingPoolStatus;
import com.SWP391.horserace.predictions.entity.Prediction;
import com.SWP391.horserace.predictions.entity.PredictionStatus;
import com.SWP391.horserace.predictions.entity.PredictionType;
import com.SWP391.horserace.predictions.repository.BettingPoolRepository;
import com.SWP391.horserace.predictions.repository.PredictionRepository;
import com.SWP391.horserace.predictions.service.PredictionService;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.races.entity.RaceEntryStatus;
import com.SWP391.horserace.races.entity.RaceStatus;
import com.SWP391.horserace.races.repository.RaceEntryRepository;
import com.SWP391.horserace.races.repository.RaceRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import com.SWP391.horserace.wallets.entity.EntryType;
import com.SWP391.horserace.wallets.entity.TxnCategory;
import com.SWP391.horserace.wallets.service.WalletLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PredictionServiceImpl implements PredictionService {

    /** Bets below this floor are rejected (VND). */
    private static final BigDecimal MIN_STAKE = new BigDecimal("10000");
    /** House rake applied to a pool at creation time (pari-mutuel take-out). */
    private static final BigDecimal DEFAULT_RAKE_PERCENT = new BigDecimal("0.15");
    /** Only straight single-runner bets are supported for money movement. */
    private static final Set<PredictionType> SUPPORTED_BET_TYPES =
            Set.of(PredictionType.WIN, PredictionType.PLACE, PredictionType.SHOW);

    private final PredictionRepository predictionRepository;
    private final BettingPoolRepository bettingPoolRepository;
    private final RaceRepository raceRepository;
    private final RaceEntryRepository raceEntryRepository;
    private final UserRepository userRepository;
    private final WalletLedgerService walletLedgerService;

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

        // 3. Validate Entry — REQUIRED for every WIN/PLACE/SHOW bet (they stake on a single runner).
        //    Without this a request with a null entry would take real money that no runner can settle
        //    against and that live-odds silently excludes.
        if (request.getPredictedEntryId() == null) {
            throw new AppException(ErrorCode.PREDICTION_ENTRY_REQUIRED);
        }
        RaceEntry predictedEntry = raceEntryRepository.findById(request.getPredictedEntryId())
                .orElseThrow(() -> new AppException(ErrorCode.PREDICTION_ENTRY_NOT_FOUND));
        if (!predictedEntry.getRace().getRaceId().equals(race.getRaceId())) {
            throw new AppException(ErrorCode.PREDICTION_ENTRY_MISMATCH);
        }
        if (predictedEntry.getStatus() == RaceEntryStatus.SCRATCHED) {
            // Don't take money on a runner that's already out.
            throw new AppException(ErrorCode.PREDICTION_ENTRY_SCRATCHED);
        }

        // 4. Check Uniqueness constraint (race_id, spectator_user_id, prediction_type, predicted_entry_id)
        boolean alreadyPredicted = predictionRepository.existsByRace_RaceIdAndSpectator_UserIdAndPredictionTypeAndPredictedEntry_EntryId(
                race.getRaceId(), userId, request.getPredictionType(), request.getPredictedEntryId()
        );
        if (alreadyPredicted) {
            throw new AppException(ErrorCode.PREDICTION_ALREADY_EXISTS);
        }

        // 5. Validate bet type + minimum stake BEFORE any DB write / wallet debit.
        if (!SUPPORTED_BET_TYPES.contains(request.getPredictionType())) {
            throw new AppException(ErrorCode.BET_TYPE_UNSUPPORTED);
        }
        if (request.getStakeAmount().compareTo(MIN_STAKE) < 0) {
            throw new AppException(ErrorCode.BET_AMOUNT_TOO_LOW);
        }

        // 6. Get or Create Betting Pool (new pool carries the house rake).
        BettingPool pool = bettingPoolRepository.findByRace_RaceIdAndPredictionType(race.getRaceId(), request.getPredictionType())
                .orElseGet(() -> {
                    BettingPool newPool = BettingPool.builder()
                            .race(race)
                            .predictionType(request.getPredictionType())
                            .rakePercent(DEFAULT_RAKE_PERCENT)
                            .status(BettingPoolStatus.OPEN)
                            .build();
                    return bettingPoolRepository.save(newPool);
                });

        if (pool.getStatus() != BettingPoolStatus.OPEN) {
            // Never debit against a pool that no longer accepts money.
            throw new AppException(ErrorCode.BETTING_POOL_CLOSED);
        }

        // 7. Get User Reference + persist the Prediction (PENDING) to obtain its id.
        User spectator = userRepository.getReferenceById(userId);
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

        // 8. Debit the wallet inside THIS @Transactional. INSUFFICIENT_BALANCE / WALLET_INACTIVE
        //    propagate and roll back the prediction save + pool add (do NOT catch/swallow).
        walletLedgerService.applyEntry(userId, EntryType.DEBIT, TxnCategory.BET_STAKE,
                request.getStakeAmount(), "PREDICTION", prediction.getPredictionId());

        // 9. Add stake to the pool ONLY after a successful debit.
        pool.setTotalStake(pool.getTotalStake().add(request.getStakeAmount()));
        bettingPoolRepository.save(pool);

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

        // Refund the stake back to the wallet inside THIS @Transactional.
        walletLedgerService.applyEntry(userId, EntryType.CREDIT, TxnCategory.REFUND,
                prediction.getStakeAmount(), "PREDICTION", prediction.getPredictionId());

        prediction.setStatus(PredictionStatus.VOID);
        predictionRepository.save(prediction);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PoolOddsResponse> getLivePoolOdds(UUID raceId) {
        List<BettingPool> pools = bettingPoolRepository.findByRace_RaceId(raceId);
        // Authoritative source: SUM live (PENDING, non-scratched) prediction stakes — NOT the
        // drift-prone pool.totalStake counter (un-locked, can lose updates under concurrent bets).
        List<Prediction> livePredictions =
                predictionRepository.findByRace_RaceIdAndStatus(raceId, PredictionStatus.PENDING);

        List<PoolOddsResponse> result = new ArrayList<>();
        for (BettingPool pool : pools) {
            Map<UUID, BigDecimal> stakeByEntry = new LinkedHashMap<>();
            for (Prediction p : livePredictions) {
                if (p.getPredictionType() != pool.getPredictionType()) {
                    continue;
                }
                RaceEntry entry = p.getPredictedEntry();
                if (entry == null || entry.getStatus() == RaceEntryStatus.SCRATCHED) {
                    continue;
                }
                BigDecimal stake = p.getStakeAmount() != null ? p.getStakeAmount() : BigDecimal.ZERO;
                stakeByEntry.merge(entry.getEntryId(), stake, BigDecimal::add);
            }

            BigDecimal totalLiveStake = stakeByEntry.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal rake = pool.getRakePercent() != null ? pool.getRakePercent() : BigDecimal.ZERO;
            BigDecimal distributable = totalLiveStake.multiply(BigDecimal.ONE.subtract(rake));

            List<PoolOddsResponse.RunnerOdds> runners = new ArrayList<>();
            for (Map.Entry<UUID, BigDecimal> e : stakeByEntry.entrySet()) {
                BigDecimal stakeOnRunner = e.getValue();
                // Zero-stake runner is omitted (never in the map); guard against divide-by-zero.
                BigDecimal estimated = stakeOnRunner.signum() == 0
                        ? null
                        : distributable.divide(stakeOnRunner, 4, RoundingMode.HALF_UP);
                runners.add(PoolOddsResponse.RunnerOdds.builder()
                        .entryId(e.getKey())
                        .stakeOnRunner(stakeOnRunner)
                        .estimatedPayoutPerUnit(estimated)
                        .build());
            }

            result.add(PoolOddsResponse.builder()
                    .poolId(pool.getPoolId())
                    .predictionType(pool.getPredictionType())
                    .rakePercent(rake)
                    .totalLiveStake(totalLiveStake)
                    .runners(runners)
                    .build());
        }
        return result;
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
