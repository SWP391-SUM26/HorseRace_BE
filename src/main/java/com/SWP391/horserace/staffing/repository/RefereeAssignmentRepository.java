package com.SWP391.horserace.staffing.repository;

import com.SWP391.horserace.assignments.entity.RefereeAssignment;
import com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

    /** Find all assignments for a race. */
    List<RefereeAssignment> findByRace_RaceId(UUID raceId);

    /** Count active assignments per referee — used for the assignedRaceCount in StaffResponse. */
    @Query("SELECT ra.referee.userId, COUNT(ra) FROM RefereeAssignment ra "
            + "WHERE ra.status <> com.SWP391.horserace.assignments.entity.RefereeAssignmentStatus.REVOKED GROUP BY ra.referee.userId")
    List<Object[]> countActiveAssignmentsPerReferee();
}
