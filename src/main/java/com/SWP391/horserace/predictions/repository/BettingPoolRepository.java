package com.SWP391.horserace.predictions.repository;

import com.SWP391.horserace.predictions.entity.BettingPool;
import com.SWP391.horserace.predictions.entity.PredictionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BettingPoolRepository extends JpaRepository<BettingPool, UUID> {
    Optional<BettingPool> findByRace_RaceIdAndPredictionType(UUID raceId, PredictionType predictionType);

    /** All pools for a race (one per prediction type) — used by the live odds view. */
    List<BettingPool> findByRace_RaceId(UUID raceId);

    /**
     * The exactly-once settlement gate: atomically flip this pool to SETTLED only if it is not
     * already SETTLED. Returns rows affected — {@code 1} = this caller claimed the pool (proceed to
     * pay), {@code 0} = another {@code settleRace} (certify-hook, admin resettle, or sweep) already
     * claimed it (return immediately, read/credit nothing). A conditional UPDATE, NOT a read-then-check,
     * so two concurrent invocations can never both process the same pool.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BettingPool p set p.status = com.SWP391.horserace.predictions.entity.BettingPoolStatus.SETTLED "
            + "where p.poolId = :id and p.status <> com.SWP391.horserace.predictions.entity.BettingPoolStatus.SETTLED")
    int markSettledIfNotSettled(@Param("id") UUID id);

    /**
     * Backstop for the settlement sweep: distinct race ids that still have at least one non-SETTLED
     * pool AND are in a settleable terminal state — either their results are OFFICIAL (normal payout)
     * OR the race is CANCELLED (refund-all). This covers both "the process died between certify commit
     * and settlement finish" AND "a race was cancelled with money still pooled" — a cancelled race's
     * pools would otherwise be stranded forever (no OFFICIAL result, so the old OFFICIAL-only finder
     * skipped them). {@code settleRace} no-ops any id that isn't actually settleable, so this is safe.
     */
    @Query("SELECT DISTINCT p.race.raceId FROM BettingPool p "
            + "WHERE p.status <> com.SWP391.horserace.predictions.entity.BettingPoolStatus.SETTLED "
            + "AND (p.race.status = com.SWP391.horserace.races.entity.RaceStatus.CANCELLED "
            + "OR EXISTS (SELECT 1 FROM RaceResult rr WHERE rr.race = p.race "
            + "AND rr.officialityStatus = com.SWP391.horserace.races.entity.OfficialityStatus.OFFICIAL))")
    List<UUID> findRaceIdsWithUnsettledPoolsForOfficialRaces();
}
