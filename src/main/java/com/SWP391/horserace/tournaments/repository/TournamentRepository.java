package com.SWP391.horserace.tournaments.repository;

import com.SWP391.horserace.tournaments.entity.Tournament;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TournamentRepository extends JpaRepository<Tournament, UUID>, JpaSpecificationExecutor<Tournament> {
    
    boolean existsByTournamentCode(String tournamentCode);

    @Query("SELECT t FROM Tournament t LEFT JOIN FETCH t.createdBy WHERE t.tournamentId = :id")
    Optional<Tournament> findByIdWithDetails(@Param("id") UUID id);
}
