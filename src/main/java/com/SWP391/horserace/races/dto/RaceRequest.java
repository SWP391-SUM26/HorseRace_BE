package com.SWP391.horserace.races.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Body for create/update of a race.
 * <p>{@code tournamentId} is logically required on create (validated in the service) and ignored on
 * update (tournament/code are immutable) — it carries no {@code @NotNull} so a partial PUT update may
 * omit it. Race {@code status} is intentionally NOT a client-settable field: create always starts at
 * {@code SCHEDULED} and lifecycle changes go through the dedicated schedule/cancel endpoints.
 */
public record RaceRequest(
        UUID tournamentId,
        @Size(max = 255) String name,
        @Size(max = 50) String raceType,
        @Positive Integer distanceMeter,
        @Size(max = 50) String trackCondition,
        @Size(max = 50) String weatherCondition,
        OffsetDateTime scheduledStartAt,
        OffsetDateTime predictionCutoffAt,
        @Positive Integer maxParticipants,
        @Positive Integer minParticipants,
        /** Free-text venue/track name, persisted to the {@code venue} column. */
        @Size(max = 255) String venue,
        /** §D1 — optional FK to a structured venue (track). */
        UUID venueId,
        /** Total prize purse (VND); distributed per finish position via {@link #prizeDistribution}. */
        @PositiveOrZero BigDecimal totalPurse,
        /** Fee the owner pays (into the house wallet) when a horse enters this race. */
        @PositiveOrZero BigDecimal entryFee,
        /** Per-finish-position prize amounts, e.g. [{place:"1st", amount:60000000}, …]. */
        List<PrizeDistributionDto> prizeDistribution) {

    /** Convenience constructor for callers that don't set purse/fee (those fields default to null). */
    public RaceRequest(UUID tournamentId, String name, String raceType, Integer distanceMeter,
                       String trackCondition, String weatherCondition, OffsetDateTime scheduledStartAt,
                       OffsetDateTime predictionCutoffAt, Integer maxParticipants, Integer minParticipants,
                       String venue, UUID venueId) {
        this(tournamentId, name, raceType, distanceMeter, trackCondition, weatherCondition,
                scheduledStartAt, predictionCutoffAt, maxParticipants, minParticipants, venue, venueId,
                null, null, null);
    }
}
