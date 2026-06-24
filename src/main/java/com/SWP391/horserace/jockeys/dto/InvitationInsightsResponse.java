package com.SWP391.horserace.jockeys.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Invitation analytics for the logged-in jockey
 * ({@code GET /api/v1/jockeys/me/invitation-insights}, FE-v2 jockey contract #11).
 */
@Data
@Builder
public class InvitationInsightsResponse {
    /** Count of invitations received in the last 7 days. */
    private int invitationsThisWeek;
    /** thisWeek minus the count in the prior 7-day window. */
    private int weekDelta;
    /** ACCEPTED / total * 100, rounded to integer (0 when none). */
    private int acceptanceRate;
    /** Top 3 inviting owners by number of invitations sent to the caller. */
    private List<OwnerActivity> mostActiveOwners;

    @Data
    @Builder
    public static class OwnerActivity {
        private UUID ownerUserId;
        private String name;
        private int requests;
    }
}
