package com.SWP391.horserace.prizes.repository;

import com.SWP391.horserace.prizes.entity.Prize;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Prize awards (owner/jockey shares of a race's purse). */
@Repository
public interface PrizeRepository extends JpaRepository<Prize, UUID> {

    /** Whether any prize has already been awarded for a race (belt against double-award). */
    boolean existsByRace_RaceId(UUID raceId);
}
