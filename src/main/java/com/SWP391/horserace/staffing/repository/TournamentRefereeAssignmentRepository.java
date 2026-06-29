package com.SWP391.horserace.staffing.repository;

import com.SWP391.horserace.assignments.entity.TournamentRefereeAssignment;
import com.SWP391.horserace.assignments.entity.TournamentRefereeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TournamentRefereeAssignmentRepository extends JpaRepository<TournamentRefereeAssignment, UUID> {

    /** Panel for a tournament (all, newest invite first). */
    List<TournamentRefereeAssignment> findByTournament_TournamentIdOrderByInvitedAtDesc(UUID tournamentId);

    /** Panel for a tournament filtered by status. */
    List<TournamentRefereeAssignment> findByTournament_TournamentIdAndStatusOrderByInvitedAtDesc(
            UUID tournamentId, TournamentRefereeStatus status);

    /** Guard against re-inviting the same referee (any non-REVOKED row already exists). */
    boolean existsByTournament_TournamentIdAndReferee_UserIdAndStatusNot(
            UUID tournamentId, UUID refereeUserId, TournamentRefereeStatus status);

    /** A referee's own invitations in a given status (e.g. INVITED = pending), newest first. */
    List<TournamentRefereeAssignment> findByReferee_UserIdAndStatusOrderByInvitedAtDesc(
            UUID refereeUserId, TournamentRefereeStatus status);
}
