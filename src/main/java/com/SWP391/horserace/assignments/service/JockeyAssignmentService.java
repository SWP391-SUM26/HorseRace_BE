package com.SWP391.horserace.assignments.service;

import com.SWP391.horserace.assignments.dto.InvitationFilterRequest;
import com.SWP391.horserace.assignments.dto.InvitationResponse;
import com.SWP391.horserace.assignments.dto.SendInvitationRequest;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface JockeyAssignmentService {

    /** Horse owner sends an invitation to a jockey for a specific race entry. */
    InvitationResponse sendInvitation(SendInvitationRequest request, UUID currentUserId);

    /** List invitations with optional filters (status, jockey, owner) + pagination. */
    Page<InvitationResponse> getInvitations(InvitationFilterRequest filter);

    /** Jockey accepts an invitation. */
    InvitationResponse acceptInvitation(UUID assignmentId, UUID currentUserId);

    /** Jockey rejects (declines) an invitation. */
    InvitationResponse rejectInvitation(UUID assignmentId, UUID currentUserId);

    /** Horse owner cancels an invitation (soft-delete: status → CANCELLED). */
    void cancelInvitation(UUID assignmentId, UUID currentUserId);
}
