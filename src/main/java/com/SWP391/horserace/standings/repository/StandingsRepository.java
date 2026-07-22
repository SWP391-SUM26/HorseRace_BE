package com.SWP391.horserace.standings.repository;

import com.SWP391.horserace.races.entity.RaceResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Read-only ranking queries, aggregated in the DB.
 *
 * <p><b>Why these columns and not the obvious ones:</b> {@code jockey_profile.win_count},
 * {@code win_rate}, {@code rating} and {@code horse.lifetime_earnings} are never written by any
 * code path — they only ever hold whatever the seed put there. Ranking on them silently produces a
 * wrong league table (the seed's top "winner" by win_count has no actual wins). Everything here is
 * therefore derived from rows the application really maintains: {@code race_result.finish_position}
 * for placings and {@code race_entry.prize_earned} for money, restricted to OFFICIAL results so a
 * provisional or under-review race never moves the table.
 */
public interface StandingsRepository extends Repository<RaceResult, UUID> {

    interface JockeyStanding {
        UUID getJockeyUserId();
        String getName();
        long getWins();
        long getStarts();
        BigDecimal getEarnings();
    }

    interface HorseStanding {
        UUID getHorseId();
        String getHorseCode();
        String getName();
        String getOwnerName();
        long getWins();
        long getStarts();
        BigDecimal getEarnings();
    }

    interface PredictorStanding {
        UUID getUserId();
        String getName();
        long getWins();
        long getSettled();
        BigDecimal getWinnings();
    }

    @Query("""
        SELECT ja.jockey.userId       AS jockeyUserId,
               ja.jockey.fullName     AS name,
               SUM(CASE WHEN rr.finishPosition = 1 THEN 1L ELSE 0L END) AS wins,
               COUNT(rr)              AS starts,
               COALESCE(SUM(e.prizeEarned), 0) AS earnings
          FROM JockeyAssignment ja
          JOIN ja.entry e
          JOIN RaceResult rr ON rr.entry = e
         WHERE ja.status = com.SWP391.horserace.assignments.entity.JockeyAssignmentStatus.ACCEPTED
           AND rr.officialityStatus = com.SWP391.horserace.races.entity.OfficialityStatus.OFFICIAL
         GROUP BY ja.jockey.userId, ja.jockey.fullName
         ORDER BY SUM(CASE WHEN rr.finishPosition = 1 THEN 1L ELSE 0L END) DESC,
                  COALESCE(SUM(e.prizeEarned), 0) DESC
        """)
    List<JockeyStanding> rankJockeys(Pageable pageable);

    /** {@code tournamentId} is optional — pass null for the all-time table. */
    @Query("""
        SELECT h.horseId        AS horseId,
               h.horseCode      AS horseCode,
               h.name           AS name,
               o.fullName       AS ownerName,
               SUM(CASE WHEN rr.finishPosition = 1 THEN 1L ELSE 0L END) AS wins,
               COUNT(rr)        AS starts,
               COALESCE(SUM(e.prizeEarned), 0) AS earnings
          FROM RaceEntry e
          JOIN e.registration reg
          JOIN reg.horse h
          JOIN reg.owner o
          JOIN RaceResult rr ON rr.entry = e
         WHERE rr.officialityStatus = com.SWP391.horserace.races.entity.OfficialityStatus.OFFICIAL
           AND (:tournamentId IS NULL OR reg.tournament.tournamentId = :tournamentId)
           AND (:ownerUserId IS NULL OR o.userId = :ownerUserId)
         GROUP BY h.horseId, h.horseCode, h.name, o.fullName
         ORDER BY SUM(CASE WHEN rr.finishPosition = 1 THEN 1L ELSE 0L END) DESC,
                  COALESCE(SUM(e.prizeEarned), 0) DESC
        """)
    List<HorseStanding> rankHorses(@Param("tournamentId") UUID tournamentId,
                                   @Param("ownerUserId") UUID ownerUserId,
                                   Pageable pageable);

    /**
     * Bettor league table. Ranked by winnings actually paid out (the Payout rows), not by stake or
     * by ticket count — a big loser would otherwise outrank a small consistent winner.
     */
    @Query("""
        SELECT p.spectator.userId   AS userId,
               p.spectator.fullName AS name,
               SUM(CASE WHEN p.status = com.SWP391.horserace.predictions.entity.PredictionStatus.WON
                        THEN 1L ELSE 0L END) AS wins,
               COUNT(p)             AS settled,
               COALESCE(SUM(po.payoutAmount), 0) AS winnings
          FROM Prediction p
          LEFT JOIN Payout po ON po.prediction = p
         WHERE p.status IN (com.SWP391.horserace.predictions.entity.PredictionStatus.WON,
                            com.SWP391.horserace.predictions.entity.PredictionStatus.LOST)
         GROUP BY p.spectator.userId, p.spectator.fullName
         ORDER BY COALESCE(SUM(po.payoutAmount), 0) DESC,
                  SUM(CASE WHEN p.status = com.SWP391.horserace.predictions.entity.PredictionStatus.WON
                           THEN 1L ELSE 0L END) DESC
        """)
    List<PredictorStanding> rankPredictors(Pageable pageable);
}
