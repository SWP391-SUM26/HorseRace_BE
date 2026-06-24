package com.SWP391.horserace.races.repository;

import com.SWP391.horserace.races.entity.RaceResultVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Immutable audit trail of result amendments — FE-v2 Results edit (mục 5). */
@Repository
public interface RaceResultVersionRepository extends JpaRepository<RaceResultVersion, UUID> {
}
