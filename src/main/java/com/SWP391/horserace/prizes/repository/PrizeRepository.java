package com.SWP391.horserace.prizes.repository;

import com.SWP391.horserace.prizes.entity.BeneficiaryType;
import com.SWP391.horserace.prizes.entity.Prize;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Prize awards (owner/jockey shares of a race's purse). */
@Repository
public interface PrizeRepository extends JpaRepository<Prize, UUID> {

    /** Whether any prize has already been awarded for a race (belt against double-award). */
    boolean existsByRace_RaceId(UUID raceId);

    /** Guard in {@code savePrize}: the UNIQUE on prize_code would otherwise surface as a raw 500. */
    boolean existsByPrizeCode(String prizeCode);

    /**
     * What this user has actually been awarded as a beneficiary of the given type.
     *
     * <p>This is the source of truth for "what the jockey earned". The obvious alternative —
     * {@code race_entry.prize_earned} — is the HORSE's whole prize, so using it reported a rider on
     * a 10% share ten times their real income. Prize rows record the amount that actually moved.
     */
    @Query("SELECT COALESCE(SUM(p.prizeAmount), 0) FROM Prize p "
            + "WHERE p.beneficiaryUser.userId = :userId AND p.beneficiaryType = :type")
    BigDecimal sumByBeneficiary(@Param("userId") UUID userId, @Param("type") BeneficiaryType type);

    /** Per-race total: what this owner was actually awarded as prize, for each of the given races. */
    @Query("SELECT p.race.raceId AS raceId, COALESCE(SUM(p.prizeAmount), 0) AS total FROM Prize p "
            + "WHERE p.race.raceId IN :raceIds "
            + "AND p.beneficiaryType = com.SWP391.horserace.prizes.entity.BeneficiaryType.OWNER "
            + "AND p.beneficiaryUser.userId = :ownerUserId "
            + "GROUP BY p.race.raceId")
    List<RacePrizeTotal> sumOwnerPrizeByRaceIds(@Param("raceIds") Collection<UUID> raceIds,
                                                 @Param("ownerUserId") UUID ownerUserId);

    interface RacePrizeTotal {
        UUID getRaceId();
        BigDecimal getTotal();
    }

    /** Batch lookup for per-ride earnings — avoids a query per row when listing a jockey's rides. */
    List<Prize> findByPrizeCodeIn(Collection<String> prizeCodes);

    /**
     * Close a tournament's books: every AWARDED prize on its races becomes PAID.
     *
     * <p>This is a STATUS FLIP ONLY. The money moved at certify — {@code creditPrizes} credits the
     * wallets there — so paying again here would double-pay every winner. AWARDED means "the money
     * is out, the tournament is still running"; PAID means "the tournament has closed its books".
     *
     * <p>Prizes are matched through their RACE, not {@code prize.tournament_id}: {@code savePrize}
     * only ever sets {@code .race(...)}, so every prize row has a NULL tournament_id and a filter on
     * that column would silently match nothing.
     *
     * <p>Idempotent by predicate: the second call finds no AWARDED rows and updates 0.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Prize p SET p.status = com.SWP391.horserace.prizes.entity.PrizeStatus.PAID, "
            + "p.paidAt = :now "
            + "WHERE p.status = com.SWP391.horserace.prizes.entity.PrizeStatus.AWARDED "
            + "AND p.race.raceId IN (SELECT r.raceId FROM Race r "
            + "                      WHERE r.tournament.tournamentId = :tournamentId "
            + "                        AND r.deleted = false)")
    int markPaidForTournament(@Param("tournamentId") UUID tournamentId,
                              @Param("now") java.time.OffsetDateTime now);
}
