package com.SWP391.horserace.horses.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Form/context intelligence about a horse for a jockey deciding on a ride
 * ({@code GET /api/v1/horses/{id}/ride-intelligence}, FE-v2 jockey contract #7).
 */
@Data
@Builder
public class RideIntelligenceResponse {
    /** Derived from the horse's characteristic tags (TURF | DIRT), null if undetermined. */
    private String preferredSurface;
    /** scheduledStartAt of the horse's next SCHEDULED/OPEN race, null if none upcoming. */
    private OffsetDateTime postTime;
    /** horse.trainerName. */
    private String trainer;
    /** owner.fullName. */
    private String owner;
    /** Up-to-3 most-recent finish positions joined with "-", e.g. "1-2-1"; null if none. */
    private String recentForm;
    /** Short derived note; null when not available. */
    private String formNotes;
}
