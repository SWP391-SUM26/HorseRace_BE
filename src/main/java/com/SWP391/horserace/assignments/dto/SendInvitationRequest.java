package com.SWP391.horserace.assignments.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/assignments/invitations}.
 * The horse owner specifies which race entry and which jockey to invite.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendInvitationRequest {

    /** The race entry (horse slot) to assign the jockey to. */
    @NotNull(message = "entryId is required")
    private UUID entryId;

    /** The jockey user to invite. */
    @NotNull(message = "jockeyUserId is required")
    private UUID jockeyUserId;
}
