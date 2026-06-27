package com.SWP391.horserace.staffing.repository;

import com.SWP391.horserace.assignments.entity.RefereeAssignment;
import com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefereeAssignmentRepository extends JpaRepository<RefereeAssignment, UUID> {

    /** Find all non-REVOKED assignments for a given race. */
    List<RefereeAssignment> findByRace_RaceIdAndStatusNot(UUID raceId, RefereeAssignmentStatus status);

    /** Check if a referee is already assigned to a race (non-REVOKED). */
    boolean existsByRace_RaceIdAndReferee_UserIdAndStatusNot(UUID raceId, UUID refereeUserId, RefereeAssignmentStatus status);

    /** Count active (non-REVOKED) assignments for a specific referee. */
    long countByReferee_UserIdAndStatusNot(UUID refereeUserId, RefereeAssignmentStatus status);

    /** Count distinct races that have at least one active assignment. */
    @Query("SELECT COUNT(DISTINCT ra.race.raceId) FROM RefereeAssignment ra "
            + "WHERE ra.status <> com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus.REVOKED")
    long countDistinctAssignedRaces();

    /** Find active assignment by id (not REVOKED). */
    Optional<RefereeAssignment> findByRefAssignmentIdAndStatusNot(UUID refAssignmentId, RefereeAssignmentStatus status);

    /** The signed-in referee's active assignment for a race (to validate their per-race code). */
    Optional<RefereeAssignment> findFirstByRace_RaceIdAndReferee_UserIdAndStatusNot(
            UUID raceId, UUID refereeUserId, RefereeAssignmentStatus status);

    /** All active (non-REVOKED) assignments for a referee — the referee's own assignment list. */
    List<RefereeAssignment> findByReferee_UserIdAndStatusNot(UUID refereeUserId, RefereeAssignmentStatus status);

    /** Uniqueness guard when generating a new per-race referee code. */
    boolean existsByRefCode(String refCode);

    /** Find all assignments for a race. */
    List<RefereeAssignment> findByRace_RaceId(UUID raceId);

    /** Distinct race IDs a referee is actively (non-REVOKED) assigned to officiate. */
    @Query("SELECT DISTINCT ra.race.raceId FROM RefereeAssignment ra "
            + "WHERE ra.referee.userId = :refereeUserId "
            + "AND ra.status <> com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus.REVOKED")
    List<UUID> findRaceIdsByReferee(@Param("refereeUserId") UUID refereeUserId);

    /** Count active assignments per referee — used for the assignedRaceCount in StaffResponse. */
    @Query("SELECT ra.referee.userId, COUNT(ra) FROM RefereeAssignment ra "
            + "WHERE ra.status <> com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus.REVOKED GROUP BY ra.referee.userId")
    List<Object[]> countActiveAssignmentsPerReferee();
}
