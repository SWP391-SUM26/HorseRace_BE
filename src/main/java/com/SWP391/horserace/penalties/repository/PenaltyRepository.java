package com.SWP391.horserace.penalties.repository;

import com.SWP391.horserace.penalties.entity.Penalty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PenaltyRepository extends JpaRepository<Penalty, UUID> {

    /** All penalties issued for a race. */
    List<Penalty> findByRace_RaceId(UUID raceId);
}
