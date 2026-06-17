package com.SWP391.horserace.races.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
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
        @Positive Integer maxParticipants) {
}
