package com.SWP391.horserace.tournaments.repository;

import com.SWP391.horserace.tournaments.entity.Tournament;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TournamentRepository extends JpaRepository<Tournament, UUID>, JpaSpecificationExecutor<Tournament> {
    
    boolean existsByTournamentCode(String tournamentCode);

    @Query("SELECT t FROM Tournament t LEFT JOIN FETCH t.createdBy WHERE t.tournamentId = :id")
    Optional<Tournament> findByIdWithDetails(@Param("id") UUID id);

    /**
     * Tournaments this user took part in, in any capacity — the exemption that keeps a finished
     * tournament visible to the people who were actually there.
     *
     * <p>Four routes, one round-trip: the owner who entered a horse, the jockey who rode, the
     * referee who officiated, the spectator who bet. Native SQL because JPQL has no UNION, and all
     * registration statuses count (including rejected) — a user should not lose sight of something
     * they took part in because the outcome went against them.
     */
    @Query(value = """
        SELECT tr.tournament_id FROM tournament_registration tr WHERE tr.owner_user_id = :userId
        UNION
        SELECT r.tournament_id FROM race r
          JOIN race_entry e ON e.race_id = r.race_id
          JOIN jockey_assignment ja ON ja.entry_id = e.entry_id
         WHERE ja.jockey_user_id = :userId
        UNION
        SELECT r.tournament_id FROM race r
          JOIN referee_assignment ra ON ra.race_id = r.race_id
         WHERE ra.referee_user_id = :userId
        UNION
        SELECT r.tournament_id FROM race r
          JOIN prediction p ON p.race_id = r.race_id
         WHERE p.spectator_user_id = :userId
        """, nativeQuery = true)
    List<UUID> findTournamentIdsInvolvingUser(@Param("userId") UUID userId);
}
