package com.SWP391.horserace.races.repository;

import com.SWP391.horserace.races.entity.RaceEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    /** The race entry created from a registration (if it became one) — for the owner's per-race report. */
    java.util.Optional<RaceEntry> findByRegistration_RegistrationId(UUID registrationId);

    /** Entries created from a set of registrations — batch lookup for the owner's per-race report (avoids N+1). */
    java.util.List<RaceEntry> findByRegistration_RegistrationIdIn(java.util.Collection<UUID> registrationIds);

    /**
     * All entries in a race with registration + horse eagerly fetched. Used by the pre-race
     * inspection list to build horseId/horseName per entry without lazy-loading (FE-v2 §2).
     */
    @Query("""
        SELECT re FROM RaceEntry re
          JOIN FETCH re.registration r
          JOIN FETCH r.horse h
         WHERE re.race.raceId = :raceId
        """)
    java.util.List<RaceEntry> findByRaceIdWithHorse(@Param("raceId") UUID raceId);

    /**
     * The entry in a race that belongs to a specific owner (via its registration), with
     * registration, horse, and owner eagerly fetched. Used for the owner's "Your Horse Status" card.
     *
     * <p>Returns a LIST: an owner may run several horses in the same race, so this was previously
     * an Optional that blew up with NonUniqueResultException the moment that happened.
     */
    @Query("""
        SELECT re FROM RaceEntry re
          JOIN FETCH re.registration r
          JOIN FETCH r.horse h
          JOIN FETCH r.owner o
         WHERE re.race.raceId = :raceId
           AND o.userId = :ownerUserId
        """)
    List<RaceEntry> findAllByRaceIdAndOwnerUserId(@Param("raceId") UUID raceId,
                                                  @Param("ownerUserId") UUID ownerUserId);

    /**
     * One specific entry, scoped to its owner — the IDOR guard for owner-side mutations. Returning
     * empty for someone else's entry is deliberate: the caller cannot tell "not yours" from
     * "doesn't exist".
     */
    @Query("""
        SELECT re FROM RaceEntry re
          JOIN FETCH re.registration r
          JOIN FETCH r.horse h
          JOIN FETCH r.owner o
         WHERE re.entryId = :entryId
           AND re.race.raceId = :raceId
           AND o.userId = :ownerUserId
        """)
    Optional<RaceEntry> findByEntryIdAndRaceIdAndOwnerUserId(@Param("entryId") UUID entryId,
                                                             @Param("raceId") UUID raceId,
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

    /**
     * Every race entry belonging to one owner (via its registration). Used by the Owner
     * Overview to compute starts/wins/top3 over the owner's entries.
     */
    @Query("""
        SELECT re FROM RaceEntry re
          JOIN re.registration r
         WHERE r.owner.userId = :ownerUserId
        """)
    java.util.List<RaceEntry> findByOwnerUserId(@Param("ownerUserId") UUID ownerUserId);

    /**
     * The owner's entries for races that are still upcoming (SCHEDULED or OPEN), newest
     * scheduled first. Race + horse are eagerly fetched so the caller can build the
     * "upcomingRaces" cards without lazy-loading.
     */
    @Query("""
        SELECT re FROM RaceEntry re
          JOIN FETCH re.race rc
          JOIN FETCH re.registration r
          JOIN FETCH r.horse h
         WHERE r.owner.userId = :ownerUserId
           AND rc.status IN (com.SWP391.horserace.races.entity.RaceStatus.SCHEDULED,
                             com.SWP391.horserace.races.entity.RaceStatus.OPEN)
           AND rc.deleted = false
         ORDER BY rc.scheduledStartAt ASC NULLS LAST
        """)
    java.util.List<RaceEntry> findUpcomingByOwnerUserId(@Param("ownerUserId") UUID ownerUserId);

    /**
     * The owner's entries for races that have gone OFFICIAL (results certified, prize + jockey fee
     * already settled at certify time) — the source rows for the owner's race-earnings breakdown.
     * Race, tournament and horse are eagerly fetched.
     */
    @Query("""
        SELECT re FROM RaceEntry re
          JOIN FETCH re.race rc
          JOIN FETCH rc.tournament t
          JOIN FETCH re.registration r
          JOIN FETCH r.horse h
         WHERE r.owner.userId = :ownerUserId
           AND rc.status = com.SWP391.horserace.races.entity.RaceStatus.OFFICIAL
           AND rc.deleted = false
         ORDER BY rc.scheduledStartAt DESC NULLS LAST
        """)
    java.util.List<RaceEntry> findOfficialByOwnerUserId(@Param("ownerUserId") UUID ownerUserId);
}
