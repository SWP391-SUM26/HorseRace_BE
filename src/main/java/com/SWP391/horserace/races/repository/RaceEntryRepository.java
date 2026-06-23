package com.SWP391.horserace.races.repository;

import com.SWP391.horserace.races.entity.RaceEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RaceEntryRepository extends JpaRepository<RaceEntry, UUID> {

    /**
     * Find a race entry by id with registration, horse, owner, race, and tournament eagerly loaded.
     * Used by the assignment service to validate ownership and build responses.
     */
    @Query("""
        SELECT re FROM RaceEntry re
          JOIN FETCH re.registration r
          JOIN FETCH r.owner o
          JOIN FETCH r.horse h
          JOIN FETCH re.race race
          JOIN FETCH race.tournament t
         WHERE re.entryId = :entryId
        """)
    Optional<RaceEntry> findByIdWithDetails(@Param("entryId") UUID entryId);

    java.util.List<RaceEntry> findByRace_RaceId(UUID raceId);

    /**
     * The entry in a race that belongs to a specific owner (via its registration), with
     * registration, horse, and owner eagerly fetched. Used for the owner's "Your Horse Status" card.
     */
    @Query("""
        SELECT re FROM RaceEntry re
          JOIN FETCH re.registration r
          JOIN FETCH r.horse h
          JOIN FETCH r.owner o
         WHERE re.race.raceId = :raceId
           AND o.userId = :ownerUserId
        """)
    Optional<RaceEntry> findByRaceIdAndOwnerUserId(@Param("raceId") UUID raceId,
                                                   @Param("ownerUserId") UUID ownerUserId);

    long countByRace_RaceId(UUID raceId);

    boolean existsByEntryCode(String entryCode);

    /**
     * Full race history for one horse: every race entry created from any of the horse's
     * registrations, newest scheduled race first. Race + tournament are eagerly fetched so the
     * caller can build history items without lazy-loading.
     */
    @Query("""
        SELECT re FROM RaceEntry re
          JOIN FETCH re.race rc
          JOIN FETCH rc.tournament t
          JOIN re.registration r
         WHERE r.horse.horseId = :horseId
         ORDER BY rc.scheduledStartAt DESC NULLS LAST
        """)
    java.util.List<RaceEntry> findHistoryByHorseId(@Param("horseId") UUID horseId);
}
