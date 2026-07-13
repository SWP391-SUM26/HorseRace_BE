package com.SWP391.horserace.predictions.service.impl;

import com.SWP391.horserace.predictions.entity.BettingPool;
import com.SWP391.horserace.predictions.entity.BettingPoolStatus;
import com.SWP391.horserace.predictions.entity.Payout;
import com.SWP391.horserace.predictions.entity.PayoutStatus;
import com.SWP391.horserace.predictions.entity.Prediction;
import com.SWP391.horserace.predictions.entity.PredictionStatus;
import com.SWP391.horserace.predictions.entity.PredictionType;
import com.SWP391.horserace.predictions.repository.BettingPoolRepository;
import com.SWP391.horserace.predictions.repository.PayoutRepository;
import com.SWP391.horserace.predictions.repository.PredictionRepository;
import com.SWP391.horserace.predictions.service.SettlementService;
import com.SWP391.horserace.races.entity.OfficialityStatus;
import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceEntry;
import com.SWP391.horserace.races.entity.RaceEntryStatus;
import com.SWP391.horserace.races.entity.RaceResult;
import com.SWP391.horserace.races.entity.RaceStatus;
import com.SWP391.horserace.races.repository.RaceRepository;
import com.SWP391.horserace.races.repository.RaceResultRepository;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.wallets.entity.EntryType;
import com.SWP391.horserace.wallets.entity.TxnCategory;
import com.SWP391.horserace.wallets.entity.WalletTransaction;
import com.SWP391.horserace.wallets.service.HouseWalletService;
import com.SWP391.horserace.wallets.service.WalletLedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementServiceImplTest {

    @Mock RaceRepository raceRepository;
    @Mock BettingPoolRepository bettingPoolRepository;
    @Mock PredictionRepository predictionRepository;
    @Mock PayoutRepository payoutRepository;
    @Mock RaceResultRepository raceResultRepository;
    @Mock WalletLedgerService walletLedgerService;
    @Mock HouseWalletService houseWalletService;
    @Mock ObjectProvider<SettlementService> selfProvider;

    private SettlementServiceImpl service;

    private final UUID raceId = UUID.randomUUID();
    private final UUID poolId = UUID.randomUUID();
    /** The escrow/house wallet owner — every payout/refund DEBITs this row FIRST. */
    private final UUID houseId = UUID.randomUUID();

    // Entries by finishing position.
    private RaceEntry e1; // 1st
    private RaceEntry e2; // 2nd
    private RaceEntry e3; // 3rd

    @BeforeEach
    void setUp() {
        service = new SettlementServiceImpl(
                raceRepository, bettingPoolRepository, predictionRepository,
                payoutRepository, raceResultRepository, walletLedgerService, houseWalletService, selfProvider);
        // self-invocation of settlePool routes through the (unproxied) same instance in unit tests
        lenient().when(selfProvider.getObject()).thenReturn(service);
        // Central house resolution — lenient so no-op / claim-lost paths (no money moves) don't fail strict-stub.
        lenient().when(houseWalletService.houseUserId()).thenReturn(houseId);

        e1 = RaceEntry.builder().entryId(UUID.randomUUID()).status(RaceEntryStatus.FINISHED).build();
        e2 = RaceEntry.builder().entryId(UUID.randomUUID()).status(RaceEntryStatus.FINISHED).build();
        e3 = RaceEntry.builder().entryId(UUID.randomUUID()).status(RaceEntryStatus.FINISHED).build();
    }

    // ---------- helpers ----------

    private Race race(RaceStatus status) {
        return Race.builder().raceId(raceId).status(status).build();
    }

    private BettingPool pool(PredictionType type, String rake) {
        return BettingPool.builder()
                .poolId(poolId)
                .race(race(RaceStatus.OFFICIAL))
                .predictionType(type)
                .rakePercent(new BigDecimal(rake))
                .totalStake(new BigDecimal("999999999")) // deliberately wrong: math must ignore this
                .status(BettingPoolStatus.OPEN)
                .build();
    }

    private Prediction bet(PredictionType type, RaceEntry entry, String stake) {
        return Prediction.builder()
                .predictionId(UUID.randomUUID())
                .race(race(RaceStatus.OFFICIAL))
                .spectator(User.builder().userId(UUID.randomUUID()).build())
                .predictedEntry(entry)
                .predictionType(type)
                .stakeAmount(new BigDecimal(stake))
                .status(PredictionStatus.PENDING)
                .build();
    }

    private RaceResult result(RaceEntry entry, int pos) {
        return RaceResult.builder()
                .resultId(UUID.randomUUID())
                .race(race(RaceStatus.OFFICIAL))
                .entry(entry)
                .finishPosition(pos)
                .officialityStatus(OfficialityStatus.OFFICIAL)
                .build();
    }

    private void stubResultsFinishOrder() {
        when(raceResultRepository.findByRace_RaceId(raceId))
                .thenReturn(List.of(result(e1, 1), result(e2, 2), result(e3, 3)));
    }

    private void stubClaimed() {
        when(bettingPoolRepository.markSettledIfNotSettled(poolId)).thenReturn(1);
    }

    private WalletTransaction txnStub() {
        return org.mockito.Mockito.mock(WalletTransaction.class);
    }

    private static org.mockito.ArgumentMatcher<BigDecimal> money(String expected) {
        BigDecimal e = new BigDecimal(expected);
        return actual -> actual != null && actual.compareTo(e) == 0;
    }

    // ---------- WIN worked example ----------

    @Test
    void winWorkedExample_payoutPerUnit2_125_floorsTo212000() {
        BettingPool p = pool(PredictionType.WIN, "0.15");
        when(raceRepository.findById(raceId)).thenReturn(java.util.Optional.of(race(RaceStatus.OFFICIAL)));
        when(bettingPoolRepository.findByRace_RaceId(raceId)).thenReturn(List.of(p));
        stubResultsFinishOrder();
        stubClaimed();

        // Winning-horse (e1) stakes total 400,000; the winner we assert on stakes 100,000.
        Prediction winner = bet(PredictionType.WIN, e1, "100000");   // -> 212,000
        Prediction winnerBig = bet(PredictionType.WIN, e1, "300000"); // -> 637,500 -> 637,000
        Prediction loser = bet(PredictionType.WIN, e2, "600000");     // LOST
        when(predictionRepository.findByRace_RaceIdAndPredictionTypeAndStatus(
                raceId, PredictionType.WIN, PredictionStatus.PENDING))
                .thenReturn(List.of(winner, winnerBig, loser));
        when(payoutRepository.existsByPrediction_PredictionId(any())).thenReturn(false);
        when(walletLedgerService.applyEntry(any(), any(), any(), any(), any(), any())).thenReturn(txnStub());

        service.settleRace(raceId);

        // exact-VND credit for the 100,000 winner
        verify(walletLedgerService).applyEntry(eq(winner.getSpectator().getUserId()),
                eq(EntryType.CREDIT), eq(TxnCategory.BET_PAYOUT),
                argThat(money("212000")), eq("PREDICTION"), eq(winner.getPredictionId()));
        // 300,000 winner -> 637,000
        verify(walletLedgerService).applyEntry(eq(winnerBig.getSpectator().getUserId()),
                eq(EntryType.CREDIT), eq(TxnCategory.BET_PAYOUT),
                argThat(money("637000")), eq("PREDICTION"), eq(winnerBig.getPredictionId()));
        // loser never credited
        verify(walletLedgerService, never()).applyEntry(eq(loser.getSpectator().getUserId()),
                any(), any(), any(), any(), any());

        // HOUSE FIRST (two-sided payout): the house is DEBITed each payout BEFORE the winner is CREDITed it.
        InOrder o1 = inOrder(walletLedgerService);
        o1.verify(walletLedgerService).applyEntry(eq(houseId),
                eq(EntryType.DEBIT), eq(TxnCategory.BET_PAYOUT),
                argThat(money("212000")), eq("PREDICTION"), eq(winner.getPredictionId()));
        o1.verify(walletLedgerService).applyEntry(eq(winner.getSpectator().getUserId()),
                eq(EntryType.CREDIT), eq(TxnCategory.BET_PAYOUT),
                argThat(money("212000")), eq("PREDICTION"), eq(winner.getPredictionId()));
        InOrder o2 = inOrder(walletLedgerService);
        o2.verify(walletLedgerService).applyEntry(eq(houseId),
                eq(EntryType.DEBIT), eq(TxnCategory.BET_PAYOUT),
                argThat(money("637000")), eq("PREDICTION"), eq(winnerBig.getPredictionId()));
        o2.verify(walletLedgerService).applyEntry(eq(winnerBig.getSpectator().getUserId()),
                eq(EntryType.CREDIT), eq(TxnCategory.BET_PAYOUT),
                argThat(money("637000")), eq("PREDICTION"), eq(winnerBig.getPredictionId()));

        // Net conservation: the house pays OUT exactly the two payouts (212,000 + 637,000 = 849,000).
        // Active stakes = 100k + 300k + 600k = 1,000,000; theoretical net = stakes·(1−rake) = 850,000.
        // The house's total DEBIT (849,000) never exceeds net (breakage of 1,000 floors DOWN and stays in
        // the house) — so what the house holds for the race (stakes − payouts = 151,000) is ≥ the 15% rake.
        ArgumentCaptor<BigDecimal> houseOut = ArgumentCaptor.forClass(BigDecimal.class);
        verify(walletLedgerService, times(2)).applyEntry(eq(houseId),
                eq(EntryType.DEBIT), eq(TxnCategory.BET_PAYOUT), houseOut.capture(),
                eq("PREDICTION"), any());
        BigDecimal totalHouseOut = houseOut.getAllValues().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalHouseOut).isEqualByComparingTo("849000");
        BigDecimal stakes = new BigDecimal("1000000");
        BigDecimal net = stakes.multiply(new BigDecimal("0.85")); // 850,000
        assertThat(totalHouseOut).usingComparator(BigDecimal::compareTo).isLessThanOrEqualTo(net);
        BigDecimal houseRetained = stakes.subtract(totalHouseOut); // 151,000
        assertThat(houseRetained).usingComparator(BigDecimal::compareTo)
                .isGreaterThanOrEqualTo(stakes.multiply(new BigDecimal("0.15"))); // ≥ 150,000 rake

        ArgumentCaptor<Payout> pc = ArgumentCaptor.forClass(Payout.class);
        verify(payoutRepository, times(2)).save(pc.capture());
        Payout paid = pc.getAllValues().stream()
                .filter(x -> x.getPrediction() == winner).findFirst().orElseThrow();
        assertThat(paid.getPayoutAmount()).usingComparator(BigDecimal::compareTo)
                .isEqualTo(new BigDecimal("212000"));
        assertThat(paid.getStatus()).isEqualTo(PayoutStatus.PAID);
        assertThat(paid.getSettledAt()).isNotNull();

        assertThat(winner.getStatus()).isEqualTo(PredictionStatus.WON);
        assertThat(winnerBig.getStatus()).isEqualTo(PredictionStatus.WON);
        assertThat(loser.getStatus()).isEqualTo(PredictionStatus.LOST);
        verify(bettingPoolRepository).markSettledIfNotSettled(poolId);
    }

    // ---------- PLACE worked example (group split) ----------

    @Test
    void placeWorkedExample_perGroup105000_gives305000and205000() {
        BettingPool p = pool(PredictionType.PLACE, "0.15");
        when(raceRepository.findById(raceId)).thenReturn(java.util.Optional.of(race(RaceStatus.OFFICIAL)));
        when(bettingPoolRepository.findByRace_RaceId(raceId)).thenReturn(List.of(p));
        stubResultsFinishOrder();
        stubClaimed();

        Prediction a = bet(PredictionType.PLACE, e1, "200000"); // 1st -> 305,000
        Prediction b = bet(PredictionType.PLACE, e2, "100000"); // 2nd -> 205,000
        Prediction loser = bet(PredictionType.PLACE, e3, "300000"); // 3rd -> LOST for PLACE
        when(predictionRepository.findByRace_RaceIdAndPredictionTypeAndStatus(
                raceId, PredictionType.PLACE, PredictionStatus.PENDING))
                .thenReturn(List.of(a, b, loser));
        when(payoutRepository.existsByPrediction_PredictionId(any())).thenReturn(false);
        when(walletLedgerService.applyEntry(any(), any(), any(), any(), any(), any())).thenReturn(txnStub());

        service.settleRace(raceId);

        verify(walletLedgerService).applyEntry(eq(a.getSpectator().getUserId()),
                eq(EntryType.CREDIT), eq(TxnCategory.BET_PAYOUT),
                argThat(money("305000")), eq("PREDICTION"), eq(a.getPredictionId()));
        verify(walletLedgerService).applyEntry(eq(b.getSpectator().getUserId()),
                eq(EntryType.CREDIT), eq(TxnCategory.BET_PAYOUT),
                argThat(money("205000")), eq("PREDICTION"), eq(b.getPredictionId()));

        // HOUSE FIRST: house DEBITed each payout BEFORE the winner's CREDIT.
        InOrder o1 = inOrder(walletLedgerService);
        o1.verify(walletLedgerService).applyEntry(eq(houseId),
                eq(EntryType.DEBIT), eq(TxnCategory.BET_PAYOUT),
                argThat(money("305000")), eq("PREDICTION"), eq(a.getPredictionId()));
        o1.verify(walletLedgerService).applyEntry(eq(a.getSpectator().getUserId()),
                eq(EntryType.CREDIT), eq(TxnCategory.BET_PAYOUT),
                argThat(money("305000")), eq("PREDICTION"), eq(a.getPredictionId()));
        InOrder o2 = inOrder(walletLedgerService);
        o2.verify(walletLedgerService).applyEntry(eq(houseId),
                eq(EntryType.DEBIT), eq(TxnCategory.BET_PAYOUT),
                argThat(money("205000")), eq("PREDICTION"), eq(b.getPredictionId()));
        o2.verify(walletLedgerService).applyEntry(eq(b.getSpectator().getUserId()),
                eq(EntryType.CREDIT), eq(TxnCategory.BET_PAYOUT),
                argThat(money("205000")), eq("PREDICTION"), eq(b.getPredictionId()));

        // Net conservation (exact here — no breakage): active stakes = 200k+100k+300k = 600,000;
        // net = 600,000·0.85 = 510,000; house DEBITs 305,000 + 205,000 = 510,000 == net exactly, so the
        // house's net for the race (stakes − payouts = 90,000) equals the 15% rake to the VND.
        ArgumentCaptor<BigDecimal> houseOut = ArgumentCaptor.forClass(BigDecimal.class);
        verify(walletLedgerService, times(2)).applyEntry(eq(houseId),
                eq(EntryType.DEBIT), eq(TxnCategory.BET_PAYOUT), houseOut.capture(),
                eq("PREDICTION"), any());
        BigDecimal totalHouseOut = houseOut.getAllValues().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal stakes = new BigDecimal("600000");
        BigDecimal net = stakes.multiply(new BigDecimal("0.85")); // 510,000
        assertThat(totalHouseOut).isEqualByComparingTo(net);
        BigDecimal houseRetained = stakes.subtract(totalHouseOut); // 90,000
        assertThat(houseRetained).isEqualByComparingTo(stakes.multiply(new BigDecimal("0.15"))); // == rake

        assertThat(a.getStatus()).isEqualTo(PredictionStatus.WON);
        assertThat(b.getStatus()).isEqualTo(PredictionStatus.WON);
        assertThat(loser.getStatus()).isEqualTo(PredictionStatus.LOST);
    }

    // ---------- exactly-once: second settleRace / re-certify no-op ----------

    @Test
    void secondSettleRace_poolAlreadyClaimed_noReadsNoCredits() {
        BettingPool p = pool(PredictionType.WIN, "0.15");
        when(raceRepository.findById(raceId)).thenReturn(java.util.Optional.of(race(RaceStatus.OFFICIAL)));
        when(bettingPoolRepository.findByRace_RaceId(raceId)).thenReturn(List.of(p));
        stubResultsFinishOrder();
        // claim loses -> 0 rows
        when(bettingPoolRepository.markSettledIfNotSettled(poolId)).thenReturn(0);

        service.settleRace(raceId);

        verify(bettingPoolRepository).markSettledIfNotSettled(poolId);
        verify(predictionRepository, never()).findByRace_RaceIdAndPredictionTypeAndStatus(any(), any(), any());
        verify(walletLedgerService, never()).applyEntry(any(), any(), any(), any(), any(), any());
        verify(payoutRepository, never()).save(any());
    }

    // ---------- concurrent double settle: claim returns 1 then 0 ----------

    @Test
    void concurrentDoubleSettle_firstClaimsSecondNoOps() {
        BettingPool p = pool(PredictionType.WIN, "0.15");
        when(raceRepository.findById(raceId)).thenReturn(java.util.Optional.of(race(RaceStatus.OFFICIAL)));
        when(bettingPoolRepository.findByRace_RaceId(raceId)).thenReturn(List.of(p));
        stubResultsFinishOrder();
        when(bettingPoolRepository.markSettledIfNotSettled(poolId)).thenReturn(1, 0);

        Prediction winner = bet(PredictionType.WIN, e1, "100000");
        Prediction loser = bet(PredictionType.WIN, e2, "600000");
        Prediction winner2 = bet(PredictionType.WIN, e1, "300000");
        when(predictionRepository.findByRace_RaceIdAndPredictionTypeAndStatus(
                raceId, PredictionType.WIN, PredictionStatus.PENDING))
                .thenReturn(List.of(winner, winner2, loser));
        when(payoutRepository.existsByPrediction_PredictionId(any())).thenReturn(false);
        when(walletLedgerService.applyEntry(any(), any(), any(), any(), any(), any())).thenReturn(txnStub());

        service.settleRace(raceId); // claims (1) and pays
        service.settleRace(raceId); // claim returns 0 -> no-op

        // predictions loaded exactly once (only the winning claim proceeded)
        verify(predictionRepository, times(1)).findByRace_RaceIdAndPredictionTypeAndStatus(
                raceId, PredictionType.WIN, PredictionStatus.PENDING);
        // Two winners, each paid two-sided (house DEBIT + winner CREDIT) => 4 ledger calls total.
        // (Was times(2) pre-house-wallet; house-first doubles the per-winner call count.)
        verify(walletLedgerService, times(4)).applyEntry(any(), any(), any(), any(), any(), any());
        // ...but still exactly ONE CREDIT payout per winner (the "one payout per winner" invariant holds).
        verify(walletLedgerService, times(2)).applyEntry(
                argThat(id -> !id.equals(houseId)), eq(EntryType.CREDIT), eq(TxnCategory.BET_PAYOUT),
                any(), any(), any());
    }

    // ---------- no-winner refund-all (no rake) ----------

    @Test
    void noWinner_refundsAllActiveStakes_noRake() {
        BettingPool p = pool(PredictionType.WIN, "0.15");
        when(raceRepository.findById(raceId)).thenReturn(java.util.Optional.of(race(RaceStatus.OFFICIAL)));
        when(bettingPoolRepository.findByRace_RaceId(raceId)).thenReturn(List.of(p));
        stubResultsFinishOrder();
        stubClaimed();

        // nobody bet on e1 (the winner); both bet on losers
        Prediction b1 = bet(PredictionType.WIN, e2, "150000");
        Prediction b2 = bet(PredictionType.WIN, e3, "250000");
        when(predictionRepository.findByRace_RaceIdAndPredictionTypeAndStatus(
                raceId, PredictionType.WIN, PredictionStatus.PENDING))
                .thenReturn(List.of(b1, b2));
        when(walletLedgerService.applyEntry(any(), any(), any(), any(), any(), any())).thenReturn(txnStub());

        service.settleRace(raceId);

        // full stakes refunded, no rake
        verify(walletLedgerService).applyEntry(eq(b1.getSpectator().getUserId()),
                eq(EntryType.CREDIT), eq(TxnCategory.REFUND),
                argThat(money("150000")), eq("PREDICTION"), eq(b1.getPredictionId()));
        verify(walletLedgerService).applyEntry(eq(b2.getSpectator().getUserId()),
                eq(EntryType.CREDIT), eq(TxnCategory.REFUND),
                argThat(money("250000")), eq("PREDICTION"), eq(b2.getPredictionId()));

        // HOUSE FIRST on the refund path too: house DEBITed the stake BEFORE the bettor is CREDITed it.
        InOrder inOrder = inOrder(walletLedgerService);
        inOrder.verify(walletLedgerService).applyEntry(eq(houseId),
                eq(EntryType.DEBIT), eq(TxnCategory.REFUND),
                argThat(money("150000")), eq("PREDICTION"), eq(b1.getPredictionId()));
        inOrder.verify(walletLedgerService).applyEntry(eq(b1.getSpectator().getUserId()),
                eq(EntryType.CREDIT), eq(TxnCategory.REFUND),
                argThat(money("150000")), eq("PREDICTION"), eq(b1.getPredictionId()));
        assertThat(b1.getStatus()).isEqualTo(PredictionStatus.REFUNDED);
        assertThat(b2.getStatus()).isEqualTo(PredictionStatus.REFUNDED);
        verify(payoutRepository, never()).save(any());
    }

    // ---------- scratched horse: stakes refunded + excluded from math ----------

    @Test
    void scratchedHorse_stakeRefunded_andExcludedFromPariMutuel() {
        BettingPool p = pool(PredictionType.WIN, "0.15");
        when(raceRepository.findById(raceId)).thenReturn(java.util.Optional.of(race(RaceStatus.OFFICIAL)));
        when(bettingPoolRepository.findByRace_RaceId(raceId)).thenReturn(List.of(p));
        stubResultsFinishOrder();
        stubClaimed();

        RaceEntry scratched = RaceEntry.builder().entryId(UUID.randomUUID())
                .status(RaceEntryStatus.SCRATCHED).build();

        // active pool: winner e1 100,000 ; loser e2 400,000 -> activeTotal 500,000
        // net = 500,000 * 0.85 = 425,000 ; payoutPerUnit = 425,000/100,000 = 4.25 ; 100,000*4.25 = 425,000
        Prediction winner = bet(PredictionType.WIN, e1, "100000");
        Prediction loser = bet(PredictionType.WIN, e2, "400000");
        Prediction scratchedBet = bet(PredictionType.WIN, scratched, "999000"); // excluded + refunded
        when(predictionRepository.findByRace_RaceIdAndPredictionTypeAndStatus(
                raceId, PredictionType.WIN, PredictionStatus.PENDING))
                .thenReturn(List.of(winner, loser, scratchedBet));
        when(payoutRepository.existsByPrediction_PredictionId(any())).thenReturn(false);
        when(walletLedgerService.applyEntry(any(), any(), any(), any(), any(), any())).thenReturn(txnStub());

        service.settleRace(raceId);

        // scratched stake refunded
        verify(walletLedgerService).applyEntry(eq(scratchedBet.getSpectator().getUserId()),
                eq(EntryType.CREDIT), eq(TxnCategory.REFUND),
                argThat(money("999000")), eq("PREDICTION"), eq(scratchedBet.getPredictionId()));
        assertThat(scratchedBet.getStatus()).isEqualTo(PredictionStatus.REFUNDED);
        // scratched stake NOT in the pari-mutuel base: winner gets 425,000 (not diluted by 999,000)
        verify(walletLedgerService).applyEntry(eq(winner.getSpectator().getUserId()),
                eq(EntryType.CREDIT), eq(TxnCategory.BET_PAYOUT),
                argThat(money("425000")), eq("PREDICTION"), eq(winner.getPredictionId()));
        assertThat(winner.getStatus()).isEqualTo(PredictionStatus.WON);
        assertThat(loser.getStatus()).isEqualTo(PredictionStatus.LOST);
    }

    // ---------- CANCELLED race: refund all across all pools ----------

    @Test
    void cancelledRace_refundsAllActiveStakesAcrossPools_noRake() {
        BettingPool winP = pool(PredictionType.WIN, "0.15");
        BettingPool placeP = BettingPool.builder().poolId(UUID.randomUUID())
                .race(race(RaceStatus.CANCELLED)).predictionType(PredictionType.PLACE)
                .rakePercent(new BigDecimal("0.15")).status(BettingPoolStatus.OPEN)
                .totalStake(BigDecimal.ZERO).build();

        when(raceRepository.findById(raceId)).thenReturn(java.util.Optional.of(race(RaceStatus.CANCELLED)));
        when(bettingPoolRepository.findByRace_RaceId(raceId)).thenReturn(List.of(winP, placeP));
        when(bettingPoolRepository.markSettledIfNotSettled(any())).thenReturn(1);

        Prediction w1 = bet(PredictionType.WIN, e1, "100000");
        Prediction pl1 = bet(PredictionType.PLACE, e2, "70000");
        when(predictionRepository.findByRace_RaceIdAndPredictionTypeAndStatus(
                raceId, PredictionType.WIN, PredictionStatus.PENDING)).thenReturn(List.of(w1));
        when(predictionRepository.findByRace_RaceIdAndPredictionTypeAndStatus(
                raceId, PredictionType.PLACE, PredictionStatus.PENDING)).thenReturn(List.of(pl1));
        when(walletLedgerService.applyEntry(any(), any(), any(), any(), any(), any())).thenReturn(txnStub());

        service.settleRace(raceId);

        verify(walletLedgerService).applyEntry(eq(w1.getSpectator().getUserId()),
                eq(EntryType.CREDIT), eq(TxnCategory.REFUND),
                argThat(money("100000")), eq("PREDICTION"), eq(w1.getPredictionId()));
        verify(walletLedgerService).applyEntry(eq(pl1.getSpectator().getUserId()),
                eq(EntryType.CREDIT), eq(TxnCategory.REFUND),
                argThat(money("70000")), eq("PREDICTION"), eq(pl1.getPredictionId()));
        assertThat(w1.getStatus()).isEqualTo(PredictionStatus.REFUNDED);
        assertThat(pl1.getStatus()).isEqualTo(PredictionStatus.REFUNDED);
        verify(payoutRepository, never()).save(any());
        // never certify-loads finishing order for a cancelled race
        verify(raceResultRepository, never()).findByRace_RaceId(any());
    }

    // ---------- breakage: non-round payout floors down to nearest 1,000 ----------

    @Test
    void breakage_floorsNonRoundPayoutDownToNearest1000() {
        BettingPool p = pool(PredictionType.WIN, "0.10");
        when(raceRepository.findById(raceId)).thenReturn(java.util.Optional.of(race(RaceStatus.OFFICIAL)));
        when(bettingPoolRepository.findByRace_RaceId(raceId)).thenReturn(List.of(p));
        stubResultsFinishOrder();
        stubClaimed();

        // active total = 100,000 + 33,000 = 133,000 ; net = 133,000 * 0.9 = 119,700
        // winning stake = 33,000 ; payoutPerUnit = 119,700/33,000 = 3.627272...
        // 33,000 * 3.627272... = 119,700 (exact) -> floor 1,000 -> 119,000
        Prediction winner = bet(PredictionType.WIN, e1, "33000");
        Prediction loser = bet(PredictionType.WIN, e2, "100000");
        when(predictionRepository.findByRace_RaceIdAndPredictionTypeAndStatus(
                raceId, PredictionType.WIN, PredictionStatus.PENDING))
                .thenReturn(List.of(winner, loser));
        when(payoutRepository.existsByPrediction_PredictionId(any())).thenReturn(false);
        when(walletLedgerService.applyEntry(any(), any(), any(), any(), any(), any())).thenReturn(txnStub());

        service.settleRace(raceId);

        verify(walletLedgerService).applyEntry(eq(winner.getSpectator().getUserId()),
                eq(EntryType.CREDIT), eq(TxnCategory.BET_PAYOUT),
                argThat(money("119000")), eq("PREDICTION"), eq(winner.getPredictionId()));
    }

    // ---------- payout idempotency guard: existing Payout skips credit ----------

    @Test
    void existingPayout_skipsCreditAndDoesNotDoubleePay() {
        BettingPool p = pool(PredictionType.WIN, "0.15");
        when(raceRepository.findById(raceId)).thenReturn(java.util.Optional.of(race(RaceStatus.OFFICIAL)));
        when(bettingPoolRepository.findByRace_RaceId(raceId)).thenReturn(List.of(p));
        stubResultsFinishOrder();
        stubClaimed();

        Prediction winner = bet(PredictionType.WIN, e1, "100000");
        Prediction loser = bet(PredictionType.WIN, e2, "400000");
        when(predictionRepository.findByRace_RaceIdAndPredictionTypeAndStatus(
                raceId, PredictionType.WIN, PredictionStatus.PENDING))
                .thenReturn(List.of(winner, loser));
        // payout already exists for the winner
        when(payoutRepository.existsByPrediction_PredictionId(winner.getPredictionId())).thenReturn(true);

        service.settleRace(raceId);

        verify(walletLedgerService, never()).applyEntry(any(), any(), any(), any(), any(), any());
        verify(payoutRepository, never()).save(any());
    }

    // ---------- defensive: invalid rake fraction fails loud (pool left unsettled) ----------

    @Test
    void invalidRakeFraction_throws_soPoolStaysUnsettledForFix() {
        // rake >= 1 (e.g. a "15" meaning 15% wrongly stored as a 0–100 percent) would invert the math.
        BettingPool p = pool(PredictionType.WIN, "15");
        stubClaimed();

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> service.settlePool(raceId, p, Map.of(), false))
                .isInstanceOf(IllegalStateException.class);

        // never read predictions or credit on a misconfigured pool
        verify(predictionRepository, never()).findByRace_RaceIdAndPredictionTypeAndStatus(any(), any(), any());
        verify(walletLedgerService, never()).applyEntry(any(), any(), any(), any(), any(), any());
    }

    // ---------- exotic pool (EXACTA/QUINELLA) refunds all rather than mis-paying ----------

    @Test
    void exoticPoolType_refundsAllActive_ratherThanApplyingPlaceShowFormula() {
        BettingPool p = pool(PredictionType.EXACTA, "0.15");
        when(raceRepository.findById(raceId)).thenReturn(java.util.Optional.of(race(RaceStatus.OFFICIAL)));
        when(bettingPoolRepository.findByRace_RaceId(raceId)).thenReturn(List.of(p));
        stubResultsFinishOrder();
        stubClaimed();

        Prediction b1 = bet(PredictionType.EXACTA, e1, "50000");
        Prediction b2 = bet(PredictionType.EXACTA, e2, "80000");
        when(predictionRepository.findByRace_RaceIdAndPredictionTypeAndStatus(
                raceId, PredictionType.EXACTA, PredictionStatus.PENDING))
                .thenReturn(List.of(b1, b2));
        when(walletLedgerService.applyEntry(any(), any(), any(), any(), any(), any())).thenReturn(txnStub());

        service.settleRace(raceId);

        verify(walletLedgerService).applyEntry(eq(b1.getSpectator().getUserId()),
                eq(EntryType.CREDIT), eq(TxnCategory.REFUND),
                argThat(money("50000")), eq("PREDICTION"), eq(b1.getPredictionId()));
        verify(walletLedgerService).applyEntry(eq(b2.getSpectator().getUserId()),
                eq(EntryType.CREDIT), eq(TxnCategory.REFUND),
                argThat(money("80000")), eq("PREDICTION"), eq(b2.getPredictionId()));
        assertThat(b1.getStatus()).isEqualTo(PredictionStatus.REFUNDED);
        assertThat(b2.getStatus()).isEqualTo(PredictionStatus.REFUNDED);
        verify(payoutRepository, never()).save(any());
    }

    // ---------- non-official, non-cancelled race is a no-op ----------

    @Test
    void nonOfficialRace_isNoOp() {
        when(raceRepository.findById(raceId)).thenReturn(java.util.Optional.of(race(RaceStatus.FINISHED)));

        service.settleRace(raceId);

        verify(bettingPoolRepository, never()).findByRace_RaceId(any());
        verify(bettingPoolRepository, never()).markSettledIfNotSettled(any());
        verify(walletLedgerService, never()).applyEntry(any(), any(), any(), any(), any(), any());
    }
}
