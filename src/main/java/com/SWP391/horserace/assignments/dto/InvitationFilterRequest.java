package com.SWP391.horserace.assignments.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Query-parameter DTO for {@code GET /api/v1/assignments/invitations}.
 * Every field is optional — omitted fields are not included in the filter.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitationFilterRequest {

    /** Filter by assignment status: INVITED, ACCEPTED, DECLINED, CANCELLED. */
    private String status;

    /** Filter by the jockey who was invited. */
    private UUID jockeyUserId;

    /** Filter by the owner who sent the invitation. */
    private UUID ownerUserId;

    // -- pagination --
    @Builder.Default
    private Integer page = 0;

    @Builder.Default
    private Integer size = 10;

    // -- sorting --
    /** Sort field: invitedAt (default), respondedAt, createdAt. */
    @Builder.Default
    private String sortBy = "invitedAt";

    /** Sort direction: asc / desc (default: desc). */
    @Builder.Default
    private String sortDir = "desc";
}
