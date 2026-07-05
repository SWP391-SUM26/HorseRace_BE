package com.SWP391.horserace.races.repository;

import com.SWP391.horserace.races.entity.Race;
import com.SWP391.horserace.races.entity.RaceStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RaceRepository extends JpaRepository<Race, UUID>, JpaSpecificationExecutor<Race> {

    boolean existsByRaceCode(String code);

    Optional<Race> findByRaceIdAndDeletedFalse(UUID id);

    @Query("SELECT r FROM Race r LEFT JOIN FETCH r.tournament WHERE r.raceId = :id")
    Optional<Race> findByIdWithTournament(@Param("id") UUID id);

    /**
     * Races a horse can still be entered into (Enter Race modal): an APPROVED registration exists
     * for the horse in the race's tournament, the race is open (SCHEDULED/OPEN), and the horse is
     * not already entered in that race.
     */
    @Query("""
        SELECT r FROM Race r
          LEFT JOIN FETCH r.tournament t
         WHERE r.deleted = false
           AND r.status IN (com.SWP391.horserace.races.entity.RaceStatus.SCHEDULED,
                            com.SWP391.horserace.races.entity.RaceStatus.OPEN)
           AND EXISTS (SELECT 1 FROM TournamentRegistration reg
                        WHERE reg.horse.horseId = :horseId
                          AND reg.tournament = r.tournament
                          AND reg.status = com.SWP391.horserace.registrations.entity.RegistrationStatus.APPROVED)
           AND NOT EXISTS (SELECT 1 FROM RaceEntry e
                        WHERE e.race = r
                          AND e.registration.horse.horseId = :horseId)
         ORDER BY r.scheduledStartAt ASC NULLS LAST
        """)
    List<Race> findEnterableRacesForHorse(@Param("horseId") UUID horseId);

    /**
     * §D3 — count non-deleted races grouped by status, optionally scoped to one tournament.
     * Returns rows of {@code [RaceStatus, Long]}. Null {@code tournamentId} = all tournaments.
     */
    @Query("SELECT r.status, COUNT(r) FROM Race r "
            + "WHERE r.deleted = false "
            + "AND (:tournamentId IS NULL OR r.tournament.tournamentId = :tournamentId) "
            + "GROUP BY r.status")
    List<Object[]> countGroupByStatus(@Param("tournamentId") UUID tournamentId);

    /**
     * Upcoming races whose status is in {@code statuses} and that have a scheduled start, soonest
     * first. The referee dashboard takes the first row as the "next race" (FE-v2 §1).
     */
    @Query("""
        SELECT r FROM Race r
         WHERE r.deleted = false
           AND r.status IN :statuses
           AND r.scheduledStartAt IS NOT NULL
         ORDER BY r.scheduledStartAt ASC
        """)
    List<Race> findUpcomingByStatuses(@Param("statuses") Collection<RaceStatus> statuses, Pageable pageable);

    /**
     * OPEN races whose registration cutoff has passed and that have not yet had a cancel proposed —
     * candidates for the auto-cancel sweeper. Returns ids so each is processed in its own transaction.
     */
    @Query("""
        SELECT r.raceId FROM Race r
         WHERE r.status = com.SWP391.horserace.races.entity.RaceStatus.OPEN
           AND r.deleted = false
           AND r.predictionCutoffAt IS NOT NULL
           AND r.predictionCutoffAt < :now
           AND r.cancelProposedAt IS NULL
        """)
    List<java.util.UUID> findOpenRacesPastCutoffWithoutCancelProposal(@Param("now") java.time.OffsetDateTime now);

    /**
     * Atomically flag one race as cancel-proposed. Conditional on the race still being OPEN with no
     * prior proposal, and it writes ONLY {@code cancel_proposed_at} — so a concurrent {@code cancelRace}
     * (status → CANCELLED) is never clobbered by a stale full-row update. Returns rows affected
     * (0 = lost the race to a concurrent writer / already proposed, 1 = flagged).
     */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Race r SET r.cancelProposedAt = :now
         WHERE r.raceId = :raceId
           AND r.status = com.SWP391.horserace.races.entity.RaceStatus.OPEN
           AND r.cancelProposedAt IS NULL
        """)
    int markCancelProposed(@Param("raceId") java.util.UUID raceId, @Param("now") java.time.OffsetDateTime now);
}
