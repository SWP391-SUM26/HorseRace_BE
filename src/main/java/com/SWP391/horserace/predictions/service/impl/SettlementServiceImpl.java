package com.SWP391.horserace.predictions.service.impl;

import com.SWP391.horserace.predictions.entity.BettingPool;
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
import com.SWP391.horserace.notifications.service.NotificationService;
import com.SWP391.horserace.rewards.entity.Reward;
import com.SWP391.horserace.rewards.entity.RewardStatus;
import com.SWP391.horserace.rewards.entity.RewardType;
import com.SWP391.horserace.rewards.repository.RewardRepository;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.wallets.entity.EntryType;
import com.SWP391.horserace.wallets.entity.TxnCategory;
import com.SWP391.horserace.wallets.entity.WalletTransaction;
import com.SWP391.horserace.wallets.service.HouseWalletService;
import com.SWP391.horserace.wallets.service.WalletLedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pari-mutuel settlement engine. See {@link SettlementService}. All money is {@link BigDecimal};
 * intermediate division uses scale-10 HALF_UP and each per-bet winning payout is floored DOWN to the
 * nearest 1,000 VND (breakage). The authoritative pool total is recomputed from live PENDING
 * predictions — {@code BettingPool.totalStake} is display-only and never used for the math.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementServiceImpl implements SettlementService {

    private static final int DIV_SCALE = 10;
    private static final BigDecimal ONE_THOUSAND = new BigDecimal("1000");
    private static final BigDecimal ONE = BigDecimal.ONE;

    private final RaceRepository raceRepository;
    private final BettingPoolRepository bettingPoolRepository;
    private final PredictionRepository predictionRepository;
    private final PayoutRepository payoutRepository;
    private final RaceResultRepository raceResultRepository;
    private final WalletLedgerService walletLedgerService;
    private final HouseWalletService houseWalletService;
    private final RewardRepository rewardRepository;
    private final NotificationService notificationService;
    /** Self-reference so {@link #settlePool} is invoked through the proxy (own tx per pool). */
    private final ObjectProvider<SettlementService> selfProvider;

    @Override
    public void settleRace(UUID raceId) {
        Race race = raceRepository.findById(raceId).orElse(null);
        if (race == null) {
            return;
        }
        RaceStatus status = race.getStatus();
        boolean cancelled = status == RaceStatus.CANCELLED;
        // Only OFFICIAL races settle normally; CANCELLED races refund-all. Anything else is a no-op
        // (lets the sweep safely pass ids of not-yet-official races that still have OPEN pools).
        if (status != RaceStatus.OFFICIAL && !cancelled) {
            return;
        }

        Map<UUID, Integer> finishByEntry = cancelled ? Map.of() : buildFinishOrder(raceId);

        List<BettingPool> pools = bettingPoolRepository.findByRace_RaceId(raceId);
        SettlementService self = selfProvider.getObject();
        for (BettingPool pool : pools) {
            try {
                self.settlePool(raceId, pool, finishByEntry, cancelled);
            } catch (RuntimeException e) {
                // Own-tx per pool: one pool's failure must not roll back the others; the sweep /
                // admin resettle will retry the stranded pool (its status stays non-SETTLED).
                log.error("Settlement failed for pool {} of race {}", pool.getPoolId(), raceId, e);
            }
        }
    }

    /** entryId → OFFICIAL finish position, ignoring rows with no position / not yet official. */
    private Map<UUID, Integer> buildFinishOrder(UUID raceId) {
        Map<UUID, Integer> map = new HashMap<>();
        for (RaceResult rr : raceResultRepository.findByRace_RaceId(raceId)) {
            if (rr.getOfficialityStatus() != OfficialityStatus.OFFICIAL) continue;
            if (rr.getEntry() == null || rr.getFinishPosition() == null) continue;
            map.put(rr.getEntry().getEntryId(), rr.getFinishPosition());
        }
        return map;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void settlePool(UUID raceId, BettingPool pool, Map<UUID, Integer> finishByEntry, boolean cancelled) {
        // (1) EXACTLY-ONCE GATE — atomic conditional UPDATE. If we don't claim it, another invocation
        // already did: read nothing, credit nothing.
        int claimed = bettingPoolRepository.markSettledIfNotSettled(pool.getPoolId());
        if (claimed == 0) {
            return;
        }

        // Resolve the house ONCE for this pool's settle (its getOrCreateWallet write joins this
        // REQUIRES_NEW tx). Every payout/refund below DEBITs the house row FIRST — a house-DEBIT failure
        // (frozen house / overdraw backstop) or a winner-CREDIT failure rolls back the whole pool tx, so
        // the pool stays non-SETTLED and the sweep/resettle retries it (money-safe, no partial pay).
        UUID house = houseWalletService.houseUserId();

        PredictionType type = pool.getPredictionType();
        BigDecimal rake = pool.getRakePercent() != null ? pool.getRakePercent() : BigDecimal.ZERO;
        OffsetDateTime now = OffsetDateTime.now();

        // Defensive: rake is a FRACTION in [0,1) (0.15 = 15%). A value >=1 or <0 (e.g. a future admin
        // typo of "15" meaning 15% into a 0–100 column) would invert the pari-mutuel math (negative
        // net → negative payouts). Fail loud so the pool stays unsettled and visible for a fix, rather
        // than paying wrong amounts. (The claim UPDATE rolls back with this REQUIRES_NEW tx.)
        if (rake.signum() < 0 || rake.compareTo(ONE) >= 0) {
            throw new IllegalStateException("Pool " + pool.getPoolId()
                    + " has an invalid rake fraction (must be in [0,1)): " + rake);
        }

        List<Prediction> all = predictionRepository
                .findByRace_RaceIdAndPredictionTypeAndStatus(raceId, type, PredictionStatus.PENDING);

        // (2) CANCELLED race → refund every active stake, no rake.
        if (cancelled) {
            for (Prediction p : all) {
                refund(p, now, house);
            }
            return;
        }

        // (2b) Only WIN/PLACE/SHOW have a defined pari-mutuel formula here. Bet placement already
        //      restricts to those, so an EXACTA/QUINELLA pool is unreachable today — but if one ever
        //      exists, refund every stake (safe) rather than pay it with the PLACE/SHOW group formula
        //      (which is semantically wrong for exotics). Real exotic math belongs to a future phase.
        if (type != PredictionType.WIN && type != PredictionType.PLACE && type != PredictionType.SHOW) {
            for (Prediction p : all) {
                refund(p, now, house);
            }
            return;
        }

        // (3) Split scratched (predicted entry scratched or missing) vs active. Scratched are refunded
        //     and EXCLUDED from the pari-mutuel base.
        List<Prediction> active = new ArrayList<>();
        for (Prediction p : all) {
            RaceEntry entry = p.getPredictedEntry();
            if (entry == null || entry.getStatus() == RaceEntryStatus.SCRATCHED) {
                refund(p, now, house);
            } else {
                active.add(p);
            }
        }

        // (4) Winners by type rule.
        List<Prediction> winners = new ArrayList<>();
        for (Prediction p : active) {
            if (isWinner(p, finishByEntry, type)) {
                winners.add(p);
            }
        }

        // (5) No winning active bet → refund all active stakes (no rake taken).
        if (winners.isEmpty()) {
            for (Prediction p : active) {
                refund(p, now, house);
            }
            return;
        }

        BigDecimal activeTotalStake = sumStakes(active);
        BigDecimal net = activeTotalStake.multiply(ONE.subtract(rake));

        Map<Prediction, BigDecimal> payouts = (type == PredictionType.WIN)
                ? computeWinPayouts(winners, net)
                : computeGroupPayouts(winners, net, finishByEntry);

        // (6) Pay winners, mark losers LOST.
        for (Prediction p : active) {
            BigDecimal payout = payouts.get(p);
            if (payout != null) {
                payWinner(p, payout, now, house);
            } else {
                p.setStatus(PredictionStatus.LOST);
                p.setSettledAt(now);
                predictionRepository.save(p);
            }
        }
    }

    // ---------- payout math ----------

    /** WIN: payoutPerUnit = net / Σ(winning stakes); payout_i = floor(stake_i * payoutPerUnit, 1000). */
    private Map<Prediction, BigDecimal> computeWinPayouts(List<Prediction> winners, BigDecimal net) {
        BigDecimal winningStake = sumStakes(winners);
        BigDecimal payoutPerUnit = net.divide(winningStake, DIV_SCALE, RoundingMode.HALF_UP);
        Map<Prediction, BigDecimal> out = new LinkedHashMap<>();
        for (Prediction p : winners) {
            out.put(p, floorTo1000(p.getStakeAmount().multiply(payoutPerUnit)));
        }
        return out;
    }

    /**
     * PLACE/SHOW: split the profit equally across the winning finish-position groups actually present,
     * then within each group pro-rata by stake. payout_i = stake_i + profitPerGroup * stake_i / groupStake.
     */
    private Map<Prediction, BigDecimal> computeGroupPayouts(
            List<Prediction> winners, BigDecimal net, Map<UUID, Integer> finishByEntry) {
        // group winners by their finish position
        Map<Integer, List<Prediction>> byPos = new LinkedHashMap<>();
        for (Prediction p : winners) {
            int pos = finishByEntry.get(p.getPredictedEntry().getEntryId());
            byPos.computeIfAbsent(pos, k -> new ArrayList<>()).add(p);
        }
        BigDecimal winningPrincipal = sumStakes(winners);
        BigDecimal profit = net.subtract(winningPrincipal);
        BigDecimal groups = BigDecimal.valueOf(byPos.size());
        BigDecimal profitPerGroup = profit.divide(groups, DIV_SCALE, RoundingMode.HALF_UP);

        Map<Prediction, BigDecimal> out = new LinkedHashMap<>();
        for (List<Prediction> group : byPos.values()) {
            BigDecimal groupStake = sumStakes(group);
            for (Prediction p : group) {
                BigDecimal share = profitPerGroup.multiply(p.getStakeAmount())
                        .divide(groupStake, DIV_SCALE, RoundingMode.HALF_UP);
                out.put(p, floorTo1000(p.getStakeAmount().add(share)));
            }
        }
        return out;
    }

    private boolean isWinner(Prediction p, Map<UUID, Integer> finishByEntry, PredictionType type) {
        Integer pos = finishByEntry.get(p.getPredictedEntry().getEntryId());
        if (pos == null) return false;
        return switch (type) {
            case WIN -> pos == 1;
            case PLACE -> pos <= 2;
            case SHOW -> pos <= 3;
            default -> pos == 1;
        };
    }

    // ---------- ledger + status mutations ----------

    private void payWinner(Prediction p, BigDecimal payout, OffsetDateTime now, UUID house) {
        // Idempotency belt: a Payout already exists for this prediction → never pay twice (and never
        // DEBIT the house twice — the house move rides inside this same guarded path).
        if (payoutRepository.existsByPrediction_PredictionId(p.getPredictionId())) {
            return;
        }
        p.setStatus(PredictionStatus.WON);
        p.setSettledAt(now);
        predictionRepository.save(p);

        // Skip zero payouts — the ledger rejects non-positive amounts (no house move either).
        if (payout.signum() <= 0) {
            return;
        }
        // Two-sided payout, HOUSE FIRST (house -> winner): DEBIT the house wallet BEFORE crediting the
        // winner. Both calls share this pool tx; a throw on either rolls the pool back (stays unsettled).
        walletLedgerService.applyEntry(
                house, EntryType.DEBIT, TxnCategory.BET_PAYOUT, payout, "PREDICTION", p.getPredictionId());
        WalletTransaction txn = walletLedgerService.applyEntry(
                userId(p), EntryType.CREDIT, TxnCategory.BET_PAYOUT, payout, "PREDICTION", p.getPredictionId());
        Payout payoutRow = Payout.builder()
                .prediction(p)
                .payoutAmount(payout)
                .walletTransaction(txn)
                .status(PayoutStatus.PAID)
                .settledBy(null)
                .settledAt(now)
                .build();
        payoutRepository.save(payoutRow);

        grantWinReward(p, payout, now);
    }

    /**
     * Req 24 — "nhận thông báo thưởng dự đoán". Until now nothing in the codebase ever created a
     * Reward, so {@code GET /rewards/notifications} was permanently empty.
     *
     * <p>Deliberately best-effort. settlePool runs REQUIRES_NEW, so an exception thrown here would
     * roll back the whole pool — the money already moved correctly and the bettor would be left with
     * an unsettled pool over a cosmetic notification. The payout is the source of truth; the reward
     * is a notification artefact. Idempotency comes from payWinner's Payout guard: this line is only
     * reached once per prediction.
     */
    private void grantWinReward(Prediction p, BigDecimal payout, OffsetDateTime now) {
        try {
            User winner = p.getSpectator();
            if (winner == null) {
                return;
            }
            String race = p.getRace() != null && p.getRace().getName() != null
                    ? p.getRace().getName() : "cuộc đua";
            rewardRepository.save(Reward.builder()
                    .user(winner)
                    .rewardType(RewardType.BET_WIN)
                    .amount(payout)
                    // amount mirrors the payout for display only — the money was already credited by
                    // the BET_PAYOUT ledger entry above. This Reward is NOT claimable for cash, so it
                    // is created already CLAIMED to keep it out of the claimable queue.
                    .status(RewardStatus.CLAIMED)
                    .claimedAt(now)
                    .title("Thắng cược " + race)
                    .description("Vé " + p.getPredictionType() + " của bạn đã thắng. "
                            + "Tiền thưởng đã được cộng vào ví.")
                    .build());
            notificationService.notifyUser(winner.getUserId(),
                    "Bạn đã thắng cược",
                    race + ": vé " + p.getPredictionType() + " thắng, đã cộng "
                            + payout.toPlainString() + "đ vào ví.");
        } catch (RuntimeException ex) {
            log.warn("Could not create BET_WIN reward for prediction {} (payout already credited): {}",
                    p.getPredictionId(), ex.toString());
        }
    }

    private void refund(Prediction p, OffsetDateTime now, UUID house) {
        // Idempotency belt (mirrors payWinner's Payout guard): only a still-PENDING bet is refunded, so
        // a re-entry can never double-refund. Settled bets (WON/LOST/REFUNDED) are skipped.
        if (p.getStatus() != PredictionStatus.PENDING) {
            return;
        }
        p.setStatus(PredictionStatus.REFUNDED);
        p.setSettledAt(now);
        predictionRepository.save(p);
        BigDecimal stake = p.getStakeAmount();
        if (stake == null || stake.signum() <= 0) {
            return;
        }
        // Two-sided refund, HOUSE FIRST (house -> bettor): DEBIT the house wallet BEFORE crediting the
        // bettor. A refund returns exactly the received stake with no rake, so the house never overdraws.
        walletLedgerService.applyEntry(
                house, EntryType.DEBIT, TxnCategory.REFUND, stake, "PREDICTION", p.getPredictionId());
        walletLedgerService.applyEntry(
                userId(p), EntryType.CREDIT, TxnCategory.REFUND, stake, "PREDICTION", p.getPredictionId());
    }

    private static UUID userId(Prediction p) {
        User u = p.getSpectator();
        return u != null ? u.getUserId() : null;
    }

    private static BigDecimal sumStakes(List<Prediction> ps) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Prediction p : ps) {
            if (p.getStakeAmount() != null) sum = sum.add(p.getStakeAmount());
        }
        return sum;
    }

    /** Breakage: floor DOWN to the nearest 1,000 VND. */
    private static BigDecimal floorTo1000(BigDecimal amount) {
        return amount.divide(ONE_THOUSAND, 0, RoundingMode.FLOOR).multiply(ONE_THOUSAND);
    }
}
