package com.SWP391.horserace.registrations.repository;

import com.SWP391.horserace.registrations.entity.RegistrationStatus;
import com.SWP391.horserace.registrations.entity.TournamentRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RegistrationRepository
        extends JpaRepository<TournamentRegistration, UUID>, JpaSpecificationExecutor<TournamentRegistration> {

    boolean existsByTournament_TournamentIdAndHorse_HorseId(UUID tournamentId, UUID horseId);

    /** The single row the UNIQUE(tournament_id, horse_id) constraint permits, live or terminated. */
    java.util.Optional<TournamentRegistration> findFirstByTournament_TournamentIdAndHorse_HorseId(
            UUID tournamentId, UUID horseId);

    boolean existsByRegistrationCode(String registrationCode);

    /** Resolve a horse's registration for a given tournament in a particular status (e.g. APPROVED). */
    Optional<TournamentRegistration> findFirstByHorse_HorseIdAndTournament_TournamentIdAndStatus(
            UUID horseId, UUID tournamentId, RegistrationStatus status);

    @Query("SELECT r FROM TournamentRegistration r "
            + "JOIN FETCH r.owner "
            + "JOIN FETCH r.tournament "
            + "JOIN FETCH r.horse "
            + "WHERE r.registrationId = :id")
    Optional<TournamentRegistration> findByIdWithDetails(@Param("id") UUID id);

    /** Count of APPROVED (i.e. registered/confirmed) entries for a tournament — surfaced in TournamentResponse (§C4). */
    long countByTournament_TournamentIdAndStatus(UUID tournamentId, RegistrationStatus status);

    /** All of an owner's race-targeted registrations (race + horse + tournament fetched) for the per-race report. */
    @Query("SELECT r FROM TournamentRegistration r "
            + "JOIN FETCH r.horse h "
            + "JOIN FETCH r.race rc "
            + "LEFT JOIN FETCH r.tournament t "
            + "WHERE r.owner.userId = :ownerUserId AND r.race IS NOT NULL "
            + "ORDER BY r.createdAt DESC")
    List<TournamentRegistration> findOwnerRaceRegistrations(@Param("ownerUserId") UUID ownerUserId);

    // ---- KPI aggregate (FE-v2 §7 registration stats) ----

    /** Status -> count over all registrations (used to build the KPI aggregate). */
    @Query("SELECT r.status AS status, COUNT(r) AS cnt FROM TournamentRegistration r GROUP BY r.status")
    List<StatusCount> countGroupByStatus();

    /** Status -> count scoped to a single tournament. */
    @Query("SELECT r.status AS status, COUNT(r) AS cnt FROM TournamentRegistration r "
            + "WHERE r.tournament.tournamentId = :tournamentId GROUP BY r.status")
    List<StatusCount> countGroupByStatusForTournament(@Param("tournamentId") UUID tournamentId);

    /** APPROVED registrations for a tournament that have NO race entry in the given race (#8). */
    @Query("""
        SELECT r FROM TournamentRegistration r
          JOIN FETCH r.horse h
          JOIN FETCH r.owner o
         WHERE r.tournament.tournamentId = :tournamentId
           AND r.status = com.SWP391.horserace.registrations.entity.RegistrationStatus.APPROVED
           AND NOT EXISTS (
             SELECT 1 FROM RaceEntry re
              WHERE re.registration = r AND re.race.raceId = :raceId
           )
        """)
    List<TournamentRegistration> findApprovedNotEnteredInRace(@Param("tournamentId") UUID tournamentId,
                                                              @Param("raceId") UUID raceId);

    /** Projection row for the group-by-status KPI query. */
    interface StatusCount {
        RegistrationStatus getStatus();
        long getCnt();
    }

    /**
     * Claim the right to charge this registration's entry fee. Returns 1 for the caller that won,
     * 0 for everyone else — a single atomic statement, so two concurrent charges cannot both
     * proceed. Same idiom as {@code markSettledIfNotSettled} / {@code markCancelProposed}.
     */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = false, flushAutomatically = true)
    @Query("""
        UPDATE TournamentRegistration r
           SET r.entryFeePaidAt = :now, r.entryFeeAmount = :fee
         WHERE r.registrationId = :registrationId
           AND r.entryFeePaidAt IS NULL
        """)
    int claimEntryFeeCharge(@Param("registrationId") UUID registrationId,
                            @Param("fee") java.math.BigDecimal fee,
                            @Param("now") java.time.OffsetDateTime now);

    /** Claim the right to refund. Returns 0 when nothing was paid, or when already refunded. */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = false, flushAutomatically = true)
    @Query("""
        UPDATE TournamentRegistration r
           SET r.entryFeeRefundedAt = :now
         WHERE r.registrationId = :registrationId
           AND r.entryFeePaidAt IS NOT NULL
           AND r.entryFeeRefundedAt IS NULL
        """)
    int claimEntryFeeRefund(@Param("registrationId") UUID registrationId,
                            @Param("now") java.time.OffsetDateTime now);

    /**
     * Registrations on a race that paid and have not been refunded — what a race cancellation must
     * pay back.
     *
     * <p>Driven off registrations, NOT race_entry: a registration can pay at submit and never be
     * approved, so it has no race_entry row at all. Iterating entries would strand that money.
     * Returns a List so an unstubbed mock yields empty rather than null.
     */
    @Query("""
        SELECT r FROM TournamentRegistration r
          JOIN FETCH r.owner
         WHERE r.race.raceId = :raceId
           AND r.entryFeePaidAt IS NOT NULL
           AND r.entryFeeRefundedAt IS NULL
        """)
    List<TournamentRegistration> findUnrefundedPaidByRace(@Param("raceId") UUID raceId);
}
