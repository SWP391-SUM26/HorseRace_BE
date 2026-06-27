package com.SWP391.horserace.staffing.service;

import com.SWP391.horserace.staffing.dto.InviteTournamentRefereeRequest;
import com.SWP391.horserace.staffing.dto.TournamentRefereeAssignmentResponse;

import java.util.List;
import java.util.UUID;

/** Tournament-level referee invite/accept flow (BE contracts §E). */
public interface TournamentRefereeService {

    // -- admin --
    /** Invite a referee to officiate a whole tournament → status INVITED. */
    TournamentRefereeAssignmentResponse invite(UUID currentUserId, InviteTournamentRefereeRequest request);

    /** All referee invitations for a tournament (optional status filter). */
    List<TournamentRefereeAssignmentResponse> listByTournament(UUID tournamentId, String status);

    /** Revoke an invitation/assignment → status REVOKED. */
    void revoke(UUID id);

    // -- referee --
    /** The signed-in referee's pending tournament invitations. */
    List<TournamentRefereeAssignmentResponse> listMyInvitations(UUID refereeUserId);

    /** Referee accepts a pending invitation → status ACCEPTED. */
    TournamentRefereeAssignmentResponse accept(UUID id, UUID refereeUserId);

    /** Referee declines a pending invitation → status DECLINED. */
    TournamentRefereeAssignmentResponse reject(UUID id, UUID refereeUserId);
}
