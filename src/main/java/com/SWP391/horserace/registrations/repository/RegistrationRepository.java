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

    /**
     * Counts a tournament's registrations grouped by status.
     * Returns rows of {@code [RegistrationStatus, Long]} for the tournament's non-empty status buckets.
     */
    @Query("SELECT r.status, COUNT(r) FROM TournamentRegistration r "
            + "WHERE r.tournament.tournamentId = :tournamentId "
            + "GROUP BY r.status")
    List<Object[]> countGroupByStatusForTournament(@Param("tournamentId") UUID tournamentId);

    /** Count of APPROVED (i.e. registered/confirmed) entries for a tournament — surfaced in TournamentResponse. */
    long countByTournament_TournamentIdAndStatus(UUID tournamentId, RegistrationStatus status);
}
