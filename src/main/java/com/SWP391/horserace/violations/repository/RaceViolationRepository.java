package com.SWP391.horserace.violations.repository;

import com.SWP391.horserace.violations.entity.RaceViolation;
import com.SWP391.horserace.violations.entity.ViolationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RaceViolationRepository extends JpaRepository<RaceViolation, UUID> {

    /**
     * Violations of a race with a given status (e.g. PENDING), newest first. Used by the referee
     * dashboard to derive alerts (FE-v2 §1) — entry/horse left-joined for the alert label.
     */
    @Query("""
        SELECT v FROM RaceViolation v
          JOIN FETCH v.race race
          LEFT JOIN FETCH v.entry e
          LEFT JOIN FETCH e.registration reg
          LEFT JOIN FETCH reg.horse h
         WHERE race.raceId = :raceId
           AND v.status = :status
         ORDER BY v.createdAt DESC
        """)
    List<RaceViolation> findByRaceIdAndStatusWithDetails(@Param("raceId") UUID raceId,
                                                         @Param("status") ViolationStatus status);

    /**
     * All violations of a race with race, entry, horse and jockey eagerly fetched so the list
     * can build {@code entityLabel} ("Race {raceCode} / {horseName} / {jockeyName}") without N+1.
     * Newest first. Optional left-joins keep entry-less / horse-less / jockey-less rows.
     */
    @Query("""
        SELECT v FROM RaceViolation v
          JOIN FETCH v.race race
          LEFT JOIN FETCH v.entry e
          LEFT JOIN FETCH e.registration reg
          LEFT JOIN FETCH reg.horse h
          LEFT JOIN FETCH v.jockey j
         WHERE race.raceId = :raceId
         ORDER BY v.createdAt DESC
        """)
    List<RaceViolation> findByRaceIdWithDetails(@Param("raceId") UUID raceId);

    /**
     * One violation with everything needed for the detail view eagerly fetched:
     * race, entry → horse, jockey, reportedBy, penalty, ruledBy.
     */
    @Query("""
        SELECT v FROM RaceViolation v
          JOIN FETCH v.race race
          LEFT JOIN FETCH v.entry e
          LEFT JOIN FETCH e.registration reg
          LEFT JOIN FETCH reg.horse h
          LEFT JOIN FETCH v.jockey j
          LEFT JOIN FETCH v.reportedBy rb
          LEFT JOIN FETCH v.penalty p
          LEFT JOIN FETCH v.ruledBy ru
         WHERE v.violationId = :violationId
        """)
    Optional<RaceViolation> findByIdWithDetails(@Param("violationId") UUID violationId);
}
