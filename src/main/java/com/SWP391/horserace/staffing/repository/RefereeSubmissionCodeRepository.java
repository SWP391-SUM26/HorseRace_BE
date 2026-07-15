package com.SWP391.horserace.staffing.repository;

import com.SWP391.horserace.staffing.entity.RefereeSubmissionCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefereeSubmissionCodeRepository extends JpaRepository<RefereeSubmissionCode, UUID> {

    /** Latest still-unconsumed code for a (race, referee) — the one to validate against. */
    Optional<RefereeSubmissionCode> findFirstByRace_RaceIdAndReferee_UserIdAndConsumedAtIsNullOrderByCreatedAtDesc(
            UUID raceId, UUID refereeUserId);

    /** Latest code issued for a (race, referee) regardless of consumption — for the resend throttle. */
    Optional<RefereeSubmissionCode> findFirstByRace_RaceIdAndReferee_UserIdOrderByCreatedAtDesc(
            UUID raceId, UUID refereeUserId);

    /**
     * Atomic single-use consume: stamp {@code consumed_at} only if it is still NULL. Returns 1 when
     * this call won the race, 0 when the code was already consumed by a concurrent request
     * (compare-and-swap — avoids the check-then-act TOCTOU under READ COMMITTED).
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE RefereeSubmissionCode c SET c.consumedAt = :now "
            + "WHERE c.codeId = :id AND c.consumedAt IS NULL")
    int markConsumed(@Param("id") UUID id, @Param("now") OffsetDateTime now);
}
