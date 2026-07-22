package com.SWP391.horserace.races.repository;

import com.SWP391.horserace.races.entity.RaceResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface RaceResultRepository extends JpaRepository<RaceResult, UUID> {

    /** Results for a set of entries — used to fill finishPosition into a horse's race history. */
    List<RaceResult> findByEntry_EntryIdIn(Collection<UUID> entryIds);

    /** The current result row for one entry in a race (FE-v2 Results bulk upsert, mục 5). */
    Optional<RaceResult> findByEntry_EntryId(UUID entryId);

    /**
     * All result rows of a race with the entry fetched — settlement reads {@code entry.entryId} +
     * {@code finishPosition} to build the finishing order without triggering lazy loads.
     */
    @Query("SELECT rr FROM RaceResult rr JOIN FETCH rr.entry e WHERE rr.race.raceId = :raceId")
    List<RaceResult> findByRace_RaceId(@Param("raceId") UUID raceId);

    /**
     * All result rows of a race, with entry + registration + horse eagerly fetched so the
     * read endpoint can build the finish order without lazy-loading (FE-v2 Results, mục 5).
     */
    @Query("""
        SELECT rr FROM RaceResult rr
          JOIN FETCH rr.entry e
          JOIN FETCH e.registration reg
          JOIN FETCH reg.horse h
         WHERE rr.race.raceId = :raceId
        """)
    List<RaceResult> findByRaceIdWithEntry(@Param("raceId") UUID raceId);

    /**
     * CN3 one-time lock: stamp {@code referee_submitted_at = now} on THIS report's result rows (the
     * given entries) that are still NULL — scoped to the submitted payload so pre-existing rows the
     * referee didn't publish aren't silently relocked. The authoritative "already published" guard is
     * {@link #existsByRace_RaceIdAndRefereeSubmittedAtIsNotNull} (checked before upsert). Atomic under
     * READ COMMITTED (row locks). Returns rows stamped.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE RaceResult r SET r.refereeSubmittedAt = :now "
            + "WHERE r.race.raceId = :raceId AND r.entry.entryId IN :entryIds AND r.refereeSubmittedAt IS NULL")
    int markRefereeSubmitted(@Param("raceId") UUID raceId,
                             @Param("entryIds") java.util.Collection<UUID> entryIds,
                             @Param("now") OffsetDateTime now);

    /** True once any result of the race has been referee-published (the report is locked). */
    boolean existsByRace_RaceIdAndRefereeSubmittedAtIsNotNull(UUID raceId);

    /** First-place finishes of horses owned by a user (admin user-detail "wins"). */
    @Query("""
        SELECT rr FROM RaceResult rr
          JOIN FETCH rr.race race
          LEFT JOIN FETCH race.tournament t
          JOIN FETCH rr.entry e
          JOIN FETCH e.registration reg
          JOIN FETCH reg.horse h
         WHERE rr.finishPosition = 1
           AND reg.owner.userId = :ownerUserId
         ORDER BY race.scheduledStartAt DESC NULLS LAST
        """)
    List<RaceResult> findWinsByOwnerUserId(@Param("ownerUserId") UUID ownerUserId);
}
