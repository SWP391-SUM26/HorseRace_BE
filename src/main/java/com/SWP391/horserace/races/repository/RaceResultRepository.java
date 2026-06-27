package com.SWP391.horserace.races.repository;

import com.SWP391.horserace.races.entity.RaceResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

@Repository
public interface RaceResultRepository extends JpaRepository<RaceResult, UUID>, JpaSpecificationExecutor<RaceResult> {

    /** Results for a set of entries — used to fill finishPosition into a horse's race history. */
    List<RaceResult> findByEntry_EntryIdIn(Collection<UUID> entryIds);

    /** The current result row for one entry in a race (FE-v2 Results bulk upsert, mục 5). */
    Optional<RaceResult> findByEntry_EntryId(UUID entryId);

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
