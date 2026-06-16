package com.SWP391.horserace.registrations.repository;

import com.SWP391.horserace.registrations.entity.TournamentRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RegistrationRepository
        extends JpaRepository<TournamentRegistration, UUID>, JpaSpecificationExecutor<TournamentRegistration> {

    boolean existsByTournament_TournamentIdAndHorse_HorseId(UUID tournamentId, UUID horseId);

    boolean existsByRegistrationCode(String registrationCode);

    @Query("SELECT r FROM TournamentRegistration r "
            + "JOIN FETCH r.owner "
            + "JOIN FETCH r.tournament "
            + "JOIN FETCH r.horse "
            + "WHERE r.registrationId = :id")
    Optional<TournamentRegistration> findByIdWithDetails(@Param("id") UUID id);
}
