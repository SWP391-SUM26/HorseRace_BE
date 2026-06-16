package com.SWP391.horserace.races.repository;

import com.SWP391.horserace.races.entity.Race;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RaceRepository extends JpaRepository<Race, UUID>, JpaSpecificationExecutor<Race> {

    boolean existsByRaceCode(String code);

    Optional<Race> findByRaceIdAndDeletedFalse(UUID id);

    @Query("SELECT r FROM Race r LEFT JOIN FETCH r.tournament WHERE r.raceId = :id")
    Optional<Race> findByIdWithTournament(@Param("id") UUID id);
}
