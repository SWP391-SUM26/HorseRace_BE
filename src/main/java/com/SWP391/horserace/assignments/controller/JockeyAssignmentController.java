package com.SWP391.horserace.assignments.controller;

import com.SWP391.horserace.assignments.dto.InvitationFilterRequest;
import com.SWP391.horserace.assignments.dto.InvitationResponse;
import com.SWP391.horserace.assignments.dto.SendInvitationRequest;
import com.SWP391.horserace.assignments.service.JockeyAssignmentService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for the Jockey Assignment (Invitation) workflow.
 *
 * <p><b>DEV mode:</b> No {@code @PreAuthorize} — matches the project convention
 * ({@code SecurityConfig} is {@code permitAll}). A {@code currentUserId} query
 * parameter is accepted as fallback when no JWT is present, for Swagger testing.
 *
 * <ul>
 *   <li>{@code POST}   — Horse owner sends an invitation to a jockey</li>
 *   <li>{@code GET}    — List invitations (filtered, paginated)</li>
 *   <li>{@code PATCH}  — Jockey accepts/rejects an invitation</li>
 *   <li>{@code DELETE} — Horse owner cancels an invitation (soft-delete)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/assignments/invitations")
@RequiredArgsConstructor
public class JockeyAssignmentController {

    private final JockeyAssignmentService assignmentService;

    // -------------------------------------------------------------------------
    // POST /api/v1/assignments/invitations — Send Invitation
    // -------------------------------------------------------------------------
    /**
     * Horse owner sends an invitation to a jockey for a specific race entry.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InvitationResponse> sendInvitation(
            @Valid @RequestBody SendInvitationRequest request,
            @RequestParam(value = "currentUserId", required = false) UUID currentUserIdParam,
            Authentication authentication) {

        UUID currentUserId = resolveUserId(authentication, currentUserIdParam);
        InvitationResponse response = assignmentService.sendInvitation(request, currentUserId);

        return ApiResponse.<InvitationResponse>builder()
                .success(true)
                .message("Invitation sent successfully")
                .data(response)
                .build();
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/assignments/invitations — Get Invitation List
    // -------------------------------------------------------------------------
    /**
     * List invitations with optional filters and pagination.
     *
     * <p>Query parameters:
     * <ul>
     *   <li>{@code status}       — INVITED, ACCEPTED, DECLINED, CANCELLED</li>
     *   <li>{@code jockeyUserId} — filter by invited jockey</li>
     *   <li>{@code ownerUserId}  — filter by owner who sent</li>
     *   <li>{@code page}         — page number (0-indexed, default 0)</li>
     *   <li>{@code size}         — page size (default 10)</li>
     *   <li>{@code sortBy}       — invitedAt (default), respondedAt, createdAt</li>
     *   <li>{@code sortDir}      — asc / desc (default: desc)</li>
     * </ul>
     */
    @GetMapping
    public ApiResponse<Page<InvitationResponse>> getInvitations(
            @ModelAttribute InvitationFilterRequest filter) {

        Page<InvitationResponse> page = assignmentService.getInvitations(filter);

        return ApiResponse.<Page<InvitationResponse>>builder()
                .success(true)
                .message("Fetched invitations")
                .data(page)
                .build();
    }

    // -------------------------------------------------------------------------
    // PATCH /api/v1/assignments/invitations/{id}/accept — Accept Invitation
    // -------------------------------------------------------------------------
    /**
     * Jockey accepts an invitation.
     */
    @PatchMapping("/{id}/accept")
    public ApiResponse<InvitationResponse> acceptInvitation(
            @PathVariable UUID id,
            @RequestParam(value = "currentUserId", required = false) UUID currentUserIdParam,
            Authentication authentication) {

        UUID currentUserId = resolveUserId(authentication, currentUserIdParam);
        InvitationResponse response = assignmentService.acceptInvitation(id, currentUserId);

        return ApiResponse.<InvitationResponse>builder()
                .success(true)
                .message("Invitation accepted")
                .data(response)
                .build();
    }

    // -------------------------------------------------------------------------
    // PATCH /api/v1/assignments/invitations/{id}/reject — Reject Invitation
    // -------------------------------------------------------------------------
    /**
     * Jockey rejects (declines) an invitation.
     */
    @PatchMapping("/{id}/reject")
    public ApiResponse<InvitationResponse> rejectInvitation(
            @PathVariable UUID id,
            @RequestParam(value = "currentUserId", required = false) UUID currentUserIdParam,
            Authentication authentication) {

        UUID currentUserId = resolveUserId(authentication, currentUserIdParam);
        InvitationResponse response = assignmentService.rejectInvitation(id, currentUserId);

        return ApiResponse.<InvitationResponse>builder()
                .success(true)
                .message("Invitation rejected")
                .data(response)
                .build();
    }

    // -------------------------------------------------------------------------
    // DELETE /api/v1/assignments/invitations/{id} — Cancel Invitation
    // -------------------------------------------------------------------------
    /**
     * Horse owner cancels an invitation (soft-delete: status → CANCELLED).
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> cancelInvitation(
            @PathVariable UUID id,
            @RequestParam(value = "currentUserId", required = false) UUID currentUserIdParam,
            Authentication authentication) {

        UUID currentUserId = resolveUserId(authentication, currentUserIdParam);
        assignmentService.cancelInvitation(id, currentUserId);

        return ApiResponse.<Void>builder()
                .success(true)
                .message("Invitation cancelled")
                .build();
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Resolves the current user id from either:
     * <ol>
     *   <li>The JWT authentication principal (production path), or</li>
     *   <li>The {@code currentUserId} query parameter (DEV/Swagger fallback).</li>
     * </ol>
     *
     * @throws AppException if neither source provides a valid user id.
     */
    private UUID resolveUserId(Authentication authentication, UUID fallbackUserId) {
        // 1. Try JWT principal
        if (authentication != null && authentication.getName() != null
                && !"anonymousUser".equals(authentication.getName())) {
            try {
                return UUID.fromString(authentication.getName());
            } catch (IllegalArgumentException ignored) {
                // not a valid UUID — fall through
            }
        }
        // 2. Try query parameter fallback (DEV mode)
        if (fallbackUserId != null) {
            return fallbackUserId;
        }
        // 3. No user identity available - return null for DEV mode bypass
        return null;
    }
}
