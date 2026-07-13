package com.SWP391.horserace.predictions.service.impl;

import com.SWP391.horserace.predictions.dto.PoolOddsResponse;
import com.SWP391.horserace.predictions.dto.PredictionRequest;
import com.SWP391.horserace.predictions.entity.BettingPool;
import com.SWP391.horserace.predictions.entity.BettingPoolStatus;
import com.SWP391.horserace.predictions.entity.Prediction;
import com.SWP391.horserace.predictions.entity.PredictionStatus;
import com.SWP391.horserace.predictions.entity.PredictionType;
import com.SWP391.horserace.predictions.repository.BettingPoolRepository;
import com.SWP391.horserace.predictions.repository.PredictionRepository;
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
import com.SWP391.horserace.wallets.service.HouseWalletService;
import com.SWP391.horserace.wallets.service.WalletLedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PredictionServiceImplTest {

    @Mock PredictionRepository predictionRepository;
    @Mock BettingPoolRepository bettingPoolRepository;
    @Mock RaceRepository raceRepository;
    @Mock RaceEntryRepository raceEntryRepository;
    @Mock UserRepository userRepository;
    @Mock WalletLedgerService walletLedgerService;
    @Mock HouseWalletService houseWalletService;

    private PredictionServiceImpl service;

    private final UUID userId = UUID.randomUUID();
    private final UUID raceId = UUID.randomUUID();
    private final UUID entryId = UUID.randomUUID();
    /** The escrow/house wallet owner — every two-sided move locks this row FIRST. */
    private final UUID houseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PredictionServiceImpl(
                predictionRepository, bettingPoolRepository, raceRepository,
                raceEntryRepository, userRepository, walletLedgerService, houseWalletService);
        // Central house resolution — lenient so guard-reject tests (which never move money) don't fail strict-stub.
        lenient().when(houseWalletService.houseUserId()).thenReturn(houseId);
    }

    /** Matches a BigDecimal by numeric value (ignoring scale). */
    private static org.mockito.ArgumentMatcher<BigDecimal> money(String expected) {
        BigDecimal e = new BigDecimal(expected);
        return actual -> actual != null && actual.compareTo(e) == 0;
    }

    // ---- helpers -----------------------------------------------------------

    /**
     * The ONLY state betting is accepted in (FR-03, revised): CLOSED with no cutoff (open through
     * CLOSED until the race leaves it). Was {@code OPEN} before the betting-window flip — the old
     * "bet on OPEN succeeds" expectation is now deliberately wrong.
     */
    private Race bettableRace() {
        return Race.builder()
                .raceId(raceId)
                .raceCode("R-1")
                .name("Race 1")
                .status(RaceStatus.CLOSED)
                .predictionCutoffAt(null)
                .build();
    }

    /** A race in an arbitrary status with an arbitrary cutoff — for the betting-window matrix. */
    private Race raceWith(RaceStatus status, OffsetDateTime cutoff) {
        return Race.builder()
                .raceId(raceId)
                .raceCode("R-1")
                .name("Race 1")
                .status(status)
                .predictionCutoffAt(cutoff)
                .build();
    }

    private RaceEntry entry() {
        return RaceEntry.builder()
                .entryId(entryId)
                .race(bettableRace())
                .status(RaceEntryStatus.ENTERED)
                .build();
    }

    private BettingPool pool(BettingPoolStatus status, BigDecimal totalStake, PredictionType type) {
        return BettingPool.builder()
                .poolId(UUID.randomUUID())
                .race(bettableRace())
                .predictionType(type)
                .totalStake(totalStake)
                .rakePercent(new BigDecimal("0.15"))
                .status(status)
                .build();
    }

    private PredictionRequest request(PredictionType type, BigDecimal stake) {
        return PredictionRequest.builder()
                .raceId(raceId)
                .predictedEntryId(entryId)
                .predictionType(type)
                .stakeAmount(stake)
                .idempotencyKey(null)
                .build();
    }

    /** Stub the read-guards that all valid-path/pool tests pass through. */
    private void stubReadGuards() {
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(bettableRace()));
        when(raceEntryRepository.findById(entryId)).thenReturn(Optional.of(entry()));
        when(predictionRepository.existsByRace_RaceIdAndSpectator_UserIdAndPredictionTypeAndPredictedEntry_EntryId(
                any(), any(), any(), any())).thenReturn(false);
    }

    // ---- submitPrediction: money movement ----------------------------------

    @Test
    void submitPrediction_winBet_debitsWalletViaBetStake_andAddsToPool_predictionPending() {
        stubReadGuards();
        BettingPool pool = pool(BettingPoolStatus.OPEN, BigDecimal.ZERO, PredictionType.WIN);
        when(bettingPoolRepository.findByRace_RaceIdAndPredictionType(raceId, PredictionType.WIN))
                .thenReturn(Optional.of(pool));
        when(userRepository.getReferenceById(userId)).thenReturn(User.builder().userId(userId).build());
        when(predictionRepository.save(any())).thenAnswer(inv -> {
            Prediction p = inv.getArgument(0);
            if (p.getPredictionId() == null) p.setPredictionId(UUID.randomUUID());
            return p;
        });

        service.submitPrediction(userId, request(PredictionType.WIN, new BigDecimal("20000")));

        // Two-sided, HOUSE FIRST: the house is CREDITed the stake BEFORE the bettor is DEBITed it
        // (the house wallet row is locked first — the deadlock-safety invariant). Money is conserved:
        // the house-credit amount equals the bettor-debit amount (both 20,000).
        InOrder inOrder = inOrder(walletLedgerService);
        inOrder.verify(walletLedgerService).applyEntry(
                eq(houseId), eq(EntryType.CREDIT), eq(TxnCategory.BET_STAKE),
                argThat(money("20000")), eq("PREDICTION"), any(UUID.class));
        inOrder.verify(walletLedgerService).applyEntry(
                eq(userId), eq(EntryType.DEBIT), eq(TxnCategory.BET_STAKE),
                argThat(money("20000")), eq("PREDICTION"), any(UUID.class));

        assertThat(pool.getTotalStake()).isEqualByComparingTo("20000");

        ArgumentCaptor<Prediction> pred = ArgumentCaptor.forClass(Prediction.class);
        verify(predictionRepository).save(pred.capture());
        assertThat(pred.getValue().getStatus()).isEqualTo(PredictionStatus.PENDING);
    }

    @Test
    void submitPrediction_stakeBelowMinimum_throwsBetAmountTooLow_andNeverTouchesLedger() {
        stubReadGuards();

        assertThatThrownBy(() -> service.submitPrediction(userId, request(PredictionType.WIN, new BigDecimal("9999"))))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.BET_AMOUNT_TOO_LOW);

        verifyNoInteractions(walletLedgerService);
        verify(predictionRepository, never()).save(any());
    }

    @Test
    void submitPrediction_exactaOrQuinella_throwsBetTypeUnsupported_andNeverTouchesLedger() {
        stubReadGuards();

        assertThatThrownBy(() -> service.submitPrediction(userId, request(PredictionType.EXACTA, new BigDecimal("20000"))))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.BET_TYPE_UNSUPPORTED);

        verifyNoInteractions(walletLedgerService);
        verify(predictionRepository, never()).save(any());
        verify(bettingPoolRepository, never()).save(any());
    }

    @Test
    void submitPrediction_nullPredictedEntry_throwsEntryRequired_andNeverTouchesLedger() {
        // A WIN/PLACE/SHOW bet with no runner would take money nothing can settle against.
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(bettableRace()));
        PredictionRequest req = PredictionRequest.builder()
                .raceId(raceId)
                .predictedEntryId(null)
                .predictionType(PredictionType.WIN)
                .stakeAmount(new BigDecimal("20000"))
                .build();

        assertThatThrownBy(() -> service.submitPrediction(userId, req))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.PREDICTION_ENTRY_REQUIRED);

        verifyNoInteractions(walletLedgerService);
        verify(predictionRepository, never()).save(any());
    }

    @Test
    void submitPrediction_scratchedRunner_throwsEntryScratched_andNeverTouchesLedger() {
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(bettableRace()));
        RaceEntry scratched = RaceEntry.builder()
                .entryId(entryId).race(bettableRace()).status(RaceEntryStatus.SCRATCHED).build();
        when(raceEntryRepository.findById(entryId)).thenReturn(Optional.of(scratched));

        assertThatThrownBy(() -> service.submitPrediction(userId, request(PredictionType.WIN, new BigDecimal("20000"))))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.PREDICTION_ENTRY_SCRATCHED);

        verifyNoInteractions(walletLedgerService);
        verify(predictionRepository, never()).save(any());
    }

    @Test
    void submitPrediction_insufficientBalance_propagates_andDoesNotAddToPool() {
        stubReadGuards();
        BettingPool pool = pool(BettingPoolStatus.OPEN, BigDecimal.ZERO, PredictionType.WIN);
        when(bettingPoolRepository.findByRace_RaceIdAndPredictionType(raceId, PredictionType.WIN))
                .thenReturn(Optional.of(pool));
        when(userRepository.getReferenceById(userId)).thenReturn(User.builder().userId(userId).build());
        when(predictionRepository.save(any())).thenAnswer(inv -> {
            Prediction p = inv.getArgument(0);
            if (p.getPredictionId() == null) p.setPredictionId(UUID.randomUUID());
            return p;
        });
        // lenient: the house CREDIT (house-first) is a separate, unstubbed invocation of this same
        // method — strict stubbing would otherwise flag it against this DEBIT-only throw stub.
        lenient().doThrow(new AppException(ErrorCode.INSUFFICIENT_BALANCE))
                .when(walletLedgerService).applyEntry(any(), eq(EntryType.DEBIT), eq(TxnCategory.BET_STAKE),
                        any(), eq("PREDICTION"), any());

        assertThatThrownBy(() -> service.submitPrediction(userId, request(PredictionType.WIN, new BigDecimal("20000"))))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);

        // HOUSE FIRST even on the failure path: the house credit is attempted BEFORE the bettor debit,
        // so it rides inside the SAME @Transactional and is rolled back together — nothing moves.
        InOrder inOrder = inOrder(walletLedgerService);
        inOrder.verify(walletLedgerService).applyEntry(
                eq(houseId), eq(EntryType.CREDIT), eq(TxnCategory.BET_STAKE), any(), eq("PREDICTION"), any());
        inOrder.verify(walletLedgerService).applyEntry(
                eq(userId), eq(EntryType.DEBIT), eq(TxnCategory.BET_STAKE), any(), eq("PREDICTION"), any());

        // Pool stake is only added AFTER a successful debit; the single @Transactional rolls the rest back.
        assertThat(pool.getTotalStake()).isEqualByComparingTo("0");
        verify(bettingPoolRepository, never()).save(any());
    }

    @Test
    void submitPrediction_closedPool_throwsBettingPoolClosed_andNeverDebits() {
        stubReadGuards();
        BettingPool pool = pool(BettingPoolStatus.CLOSED, new BigDecimal("50000"), PredictionType.WIN);
        when(bettingPoolRepository.findByRace_RaceIdAndPredictionType(raceId, PredictionType.WIN))
                .thenReturn(Optional.of(pool));

        assertThatThrownBy(() -> service.submitPrediction(userId, request(PredictionType.WIN, new BigDecimal("20000"))))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.BETTING_POOL_CLOSED);

        verifyNoInteractions(walletLedgerService);
        verify(predictionRepository, never()).save(any());
        assertThat(pool.getTotalStake()).isEqualByComparingTo("50000");
    }

    @Test
    void submitPrediction_doesNotSetLockedOddsNorPotentialPayout() {
        stubReadGuards();
        BettingPool pool = pool(BettingPoolStatus.OPEN, BigDecimal.ZERO, PredictionType.WIN);
        when(bettingPoolRepository.findByRace_RaceIdAndPredictionType(raceId, PredictionType.WIN))
                .thenReturn(Optional.of(pool));
        when(userRepository.getReferenceById(userId)).thenReturn(User.builder().userId(userId).build());
        when(predictionRepository.save(any())).thenAnswer(inv -> {
            Prediction p = inv.getArgument(0);
            if (p.getPredictionId() == null) p.setPredictionId(UUID.randomUUID());
            return p;
        });

        service.submitPrediction(userId, request(PredictionType.WIN, new BigDecimal("20000")));

        ArgumentCaptor<Prediction> pred = ArgumentCaptor.forClass(Prediction.class);
        verify(predictionRepository).save(pred.capture());
        assertThat(pred.getValue().getLockedOdds()).isNull();
        assertThat(pred.getValue().getPotentialPayout()).isNull();
    }

    @Test
    void submitPrediction_createsNewPoolWithRakeFifteenPercent() {
        stubReadGuards();
        when(bettingPoolRepository.findByRace_RaceIdAndPredictionType(raceId, PredictionType.WIN))
                .thenReturn(Optional.empty());
        when(bettingPoolRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.getReferenceById(userId)).thenReturn(User.builder().userId(userId).build());
        when(predictionRepository.save(any())).thenAnswer(inv -> {
            Prediction p = inv.getArgument(0);
            if (p.getPredictionId() == null) p.setPredictionId(UUID.randomUUID());
            return p;
        });

        service.submitPrediction(userId, request(PredictionType.WIN, new BigDecimal("20000")));

        ArgumentCaptor<BettingPool> poolCaptor = ArgumentCaptor.forClass(BettingPool.class);
        verify(bettingPoolRepository, org.mockito.Mockito.atLeastOnce()).save(poolCaptor.capture());
        BettingPool created = poolCaptor.getAllValues().get(0);
        assertThat(created.getRakePercent()).isEqualByComparingTo("0.15");
        assertThat(created.getStatus()).isEqualTo(BettingPoolStatus.OPEN);
    }

    // ---- submitPrediction: betting-window matrix (FR-03) -------------------
    // Betting is accepted ONLY when the race is CLOSED and before its cutoff.

    @Test
    void submitPrediction_nonClosedStatus_throwsRaceNotOpen_andNeverTouchesLedger() {
        for (RaceStatus notBettable : new RaceStatus[]{
                RaceStatus.SCHEDULED, RaceStatus.OPEN, RaceStatus.RUNNING,
                RaceStatus.FINISHED, RaceStatus.OFFICIAL, RaceStatus.CANCELLED}) {
            when(raceRepository.findByRaceIdAndDeletedFalse(raceId))
                    .thenReturn(Optional.of(raceWith(notBettable, null)));

            assertThatThrownBy(() -> service.submitPrediction(userId, request(PredictionType.WIN, new BigDecimal("20000"))))
                    .as("betting on %s must be rejected", notBettable)
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PREDICTION_RACE_NOT_OPEN);
        }
        verifyNoInteractions(walletLedgerService);
        verify(predictionRepository, never()).save(any());
    }

    @Test
    void submitPrediction_closedButPastCutoff_throwsRaceNotOpen_andNeverTouchesLedger() {
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId))
                .thenReturn(Optional.of(raceWith(RaceStatus.CLOSED, OffsetDateTime.now().minusHours(1))));

        assertThatThrownBy(() -> service.submitPrediction(userId, request(PredictionType.WIN, new BigDecimal("20000"))))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.PREDICTION_RACE_NOT_OPEN);

        verifyNoInteractions(walletLedgerService);
        verify(predictionRepository, never()).save(any());
    }

    @Test
    void submitPrediction_closedBeforeCutoff_succeeds_andDebitsLedger() {
        Race race = raceWith(RaceStatus.CLOSED, OffsetDateTime.now().plusHours(1));
        when(raceRepository.findByRaceIdAndDeletedFalse(raceId)).thenReturn(Optional.of(race));
        when(raceEntryRepository.findById(entryId)).thenReturn(Optional.of(entry()));
        when(predictionRepository.existsByRace_RaceIdAndSpectator_UserIdAndPredictionTypeAndPredictedEntry_EntryId(
                any(), any(), any(), any())).thenReturn(false);
        BettingPool pool = pool(BettingPoolStatus.OPEN, BigDecimal.ZERO, PredictionType.WIN);
        when(bettingPoolRepository.findByRace_RaceIdAndPredictionType(raceId, PredictionType.WIN))
                .thenReturn(Optional.of(pool));
        when(userRepository.getReferenceById(userId)).thenReturn(User.builder().userId(userId).build());
        when(predictionRepository.save(any())).thenAnswer(inv -> {
            Prediction p = inv.getArgument(0);
            if (p.getPredictionId() == null) p.setPredictionId(UUID.randomUUID());
            return p;
        });

        service.submitPrediction(userId, request(PredictionType.WIN, new BigDecimal("20000")));

        verify(walletLedgerService).applyEntry(
                eq(userId), eq(EntryType.DEBIT), eq(TxnCategory.BET_STAKE),
                any(), eq("PREDICTION"), any(UUID.class));
        assertThat(pool.getTotalStake()).isEqualByComparingTo("20000");
    }

    // ---- cancelPrediction: refund ------------------------------------------

    @Test
    void cancelPrediction_refundsViaCreditRefund_decrementsPool_andVoidsPrediction() {
        UUID predictionId = UUID.randomUUID();
        Prediction prediction = Prediction.builder()
                .predictionId(predictionId)
                .race(bettableRace())
                .predictionType(PredictionType.WIN)
                .stakeAmount(new BigDecimal("20000"))
                .status(PredictionStatus.PENDING)
                .build();
        when(predictionRepository.findByPredictionIdAndSpectatorUserId(predictionId, userId))
                .thenReturn(Optional.of(prediction));
        BettingPool pool = pool(BettingPoolStatus.OPEN, new BigDecimal("50000"), PredictionType.WIN);
        when(bettingPoolRepository.findByRace_RaceIdAndPredictionType(raceId, PredictionType.WIN))
                .thenReturn(Optional.of(pool));

        service.cancelPrediction(userId, predictionId);

        // Two-sided refund, HOUSE FIRST: the house is DEBITed the stake BEFORE the bettor is CREDITed it.
        InOrder inOrder = inOrder(walletLedgerService);
        inOrder.verify(walletLedgerService).applyEntry(
                eq(houseId), eq(EntryType.DEBIT), eq(TxnCategory.REFUND),
                argThat(money("20000")), eq("PREDICTION"), eq(predictionId));
        inOrder.verify(walletLedgerService).applyEntry(
                eq(userId), eq(EntryType.CREDIT), eq(TxnCategory.REFUND),
                argThat(money("20000")), eq("PREDICTION"), eq(predictionId));
        assertThat(pool.getTotalStake()).isEqualByComparingTo("30000");
        assertThat(prediction.getStatus()).isEqualTo(PredictionStatus.VOID);
    }

    @Test
    void cancelPrediction_nonPending_throwsCannotCancel_andNeverRefunds() {
        UUID predictionId = UUID.randomUUID();
        Prediction prediction = Prediction.builder()
                .predictionId(predictionId)
                .race(bettableRace())
                .predictionType(PredictionType.WIN)
                .stakeAmount(new BigDecimal("20000"))
                .status(PredictionStatus.WON)
                .build();
        when(predictionRepository.findByPredictionIdAndSpectatorUserId(predictionId, userId))
                .thenReturn(Optional.of(prediction));

        assertThatThrownBy(() -> service.cancelPrediction(userId, predictionId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.PREDICTION_CANNOT_CANCEL);

        verifyNoInteractions(walletLedgerService);
    }

    @Test
    void cancelPrediction_afterCutoff_throwsCannotCancel_andNeverRefunds() {
        UUID predictionId = UUID.randomUUID();
        Race race = bettableRace();
        race.setPredictionCutoffAt(OffsetDateTime.now().minusHours(1));
        Prediction prediction = Prediction.builder()
                .predictionId(predictionId)
                .race(race)
                .predictionType(PredictionType.WIN)
                .stakeAmount(new BigDecimal("20000"))
                .status(PredictionStatus.PENDING)
                .build();
        when(predictionRepository.findByPredictionIdAndSpectatorUserId(predictionId, userId))
                .thenReturn(Optional.of(prediction));

        assertThatThrownBy(() -> service.cancelPrediction(userId, predictionId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.PREDICTION_CANNOT_CANCEL);

        verifyNoInteractions(walletLedgerService);
    }

    @Test
    void cancelPrediction_raceNotClosed_throwsCannotCancel_andNeverRefunds() {
        // A PENDING prediction whose race is in any non-CLOSED status can no longer be cancelled.
        for (RaceStatus notBettable : new RaceStatus[]{
                RaceStatus.SCHEDULED, RaceStatus.OPEN, RaceStatus.RUNNING,
                RaceStatus.FINISHED, RaceStatus.OFFICIAL, RaceStatus.CANCELLED}) {
            UUID predictionId = UUID.randomUUID();
            Prediction prediction = Prediction.builder()
                    .predictionId(predictionId)
                    .race(raceWith(notBettable, null))
                    .predictionType(PredictionType.WIN)
                    .stakeAmount(new BigDecimal("20000"))
                    .status(PredictionStatus.PENDING)
                    .build();
            when(predictionRepository.findByPredictionIdAndSpectatorUserId(predictionId, userId))
                    .thenReturn(Optional.of(prediction));

            assertThatThrownBy(() -> service.cancelPrediction(userId, predictionId))
                    .as("cancelling on %s must be rejected", notBettable)
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PREDICTION_CANNOT_CANCEL);
        }
        verifyNoInteractions(walletLedgerService);
    }

    @Test
    void cancelPrediction_closedBeforeCutoff_refunds() {
        // CLOSED + future cutoff is the cancellable window: the refund path runs.
        UUID predictionId = UUID.randomUUID();
        Prediction prediction = Prediction.builder()
                .predictionId(predictionId)
                .race(raceWith(RaceStatus.CLOSED, OffsetDateTime.now().plusHours(1)))
                .predictionType(PredictionType.WIN)
                .stakeAmount(new BigDecimal("20000"))
                .status(PredictionStatus.PENDING)
                .build();
        when(predictionRepository.findByPredictionIdAndSpectatorUserId(predictionId, userId))
                .thenReturn(Optional.of(prediction));
        BettingPool pool = pool(BettingPoolStatus.OPEN, new BigDecimal("50000"), PredictionType.WIN);
        when(bettingPoolRepository.findByRace_RaceIdAndPredictionType(raceId, PredictionType.WIN))
                .thenReturn(Optional.of(pool));

        service.cancelPrediction(userId, predictionId);

        verify(walletLedgerService).applyEntry(
                eq(userId), eq(EntryType.CREDIT), eq(TxnCategory.REFUND),
                any(), eq("PREDICTION"), eq(predictionId));
        assertThat(prediction.getStatus()).isEqualTo(PredictionStatus.VOID);
    }

    // ---- getLivePoolOdds: summed live stakes, NOT pool.totalStake -----------

    @Test
    void getLivePoolOdds_computesFromSummedLivePredictions_notPoolTotalStake_omitsZeroStakeRunner() {
        UUID entryA = UUID.randomUUID();
        UUID entryB = UUID.randomUUID();

        // Deliberately drifted display counter — odds must ignore it entirely.
        BettingPool pool = pool(BettingPoolStatus.OPEN, new BigDecimal("999999"), PredictionType.WIN);
        when(bettingPoolRepository.findByRace_RaceId(raceId)).thenReturn(List.of(pool));

        Prediction pA = Prediction.builder()
                .predictionType(PredictionType.WIN)
                .predictedEntry(RaceEntry.builder().entryId(entryA).status(RaceEntryStatus.ENTERED).build())
                .stakeAmount(new BigDecimal("30000"))
                .status(PredictionStatus.PENDING)
                .build();
        Prediction pB = Prediction.builder()
                .predictionType(PredictionType.WIN)
                .predictedEntry(RaceEntry.builder().entryId(entryB).status(RaceEntryStatus.ENTERED).build())
                .stakeAmount(new BigDecimal("10000"))
                .status(PredictionStatus.PENDING)
                .build();
        when(predictionRepository.findByRace_RaceIdAndStatus(raceId, PredictionStatus.PENDING))
                .thenReturn(List.of(pA, pB));

        List<PoolOddsResponse> result = service.getLivePoolOdds(raceId);

        assertThat(result).hasSize(1);
        PoolOddsResponse winPool = result.get(0);
        // Summed live stake = 30000 + 10000 = 40000  (NOT the drifted 999999)
        assertThat(winPool.getTotalLiveStake()).isEqualByComparingTo("40000");
        assertThat(winPool.getPredictionType()).isEqualTo(PredictionType.WIN);
        assertThat(winPool.getRunners()).hasSize(2);

        // distributable = 40000 * (1 - 0.15) = 34000
        PoolOddsResponse.RunnerOdds a = winPool.getRunners().stream()
                .filter(r -> r.getEntryId().equals(entryA)).findFirst().orElseThrow();
        PoolOddsResponse.RunnerOdds b = winPool.getRunners().stream()
                .filter(r -> r.getEntryId().equals(entryB)).findFirst().orElseThrow();
        assertThat(a.getEstimatedPayoutPerUnit()).isEqualByComparingTo("1.1333"); // 34000/30000
        assertThat(b.getEstimatedPayoutPerUnit()).isEqualByComparingTo("3.4");    // 34000/10000

        // A runner with zero live stake is omitted (no prediction for it).
        assertThat(winPool.getRunners()).noneMatch(r -> r.getEstimatedPayoutPerUnit() == null);
    }
}
