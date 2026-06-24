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
}
