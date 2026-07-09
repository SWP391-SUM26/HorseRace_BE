package com.SWP391.horserace.predictions.service;

import com.SWP391.horserace.predictions.entity.BettingPool;

import java.util.Map;
import java.util.UUID;

/**
 * Pari-mutuel SETTLEMENT engine (money-OUT). Invoked as a FOLLOW-ON step AFTER a race's results are
 * certified OFFICIAL (from the controller, once certify's transaction has committed) — never inside
 * certify's transaction. Idempotent and retriable: the certify-hook, the admin resettle endpoint,
 * and the sweep job all call the same {@link #settleRace(UUID)}; the atomic per-pool status claim +
 * the {@code payout} UNIQUE constraint guarantee exactly-once payment.
 */
public interface SettlementService {

    /**
     * Settle every pool of a race. No-op unless the race is OFFICIAL (normal settlement) or CANCELLED
     * (refund-all, no rake). Each pool is settled in its own transaction via {@link #settlePool}.
     */
    void settleRace(UUID raceId);

    /**
     * Settle ONE pool in its own transaction. Atomically claims the pool (exactly-once gate); if the
     * claim is lost, returns immediately without reading predictions or crediting. Otherwise recomputes
     * the authoritative pool total from live PENDING predictions, refunds scratched stakes, pays winners
     * via the pari-mutuel formula (breakage floored to 1,000 VND), marks WON/LOST/REFUNDED.
     *
     * @param raceId         the pool's race id (passed in to avoid a lazy load of the pool's race)
     * @param pool           the pool to settle
     * @param finishByEntry  entryId → OFFICIAL finish position (empty for a cancelled race)
     * @param cancelled      true when the race is CANCELLED → refund all active stakes, no rake
     */
    void settlePool(UUID raceId, BettingPool pool, Map<UUID, Integer> finishByEntry, boolean cancelled);
}
