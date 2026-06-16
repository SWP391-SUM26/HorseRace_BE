package com.SWP391.horserace.races.dto;

import com.SWP391.horserace.races.entity.RaceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Body for create/update of a race.
 * {@code tournamentId} is logically required on create (validated in the service);
 * on update it is ignored (tournament/code are immutable).
 */
public record RaceRequest(
        @NotNull UUID tournamentId,
        @Size(max = 255) String name,
        @Size(max = 50) String raceType,
        @Positive Integer distanceMeter,
        @Size(max = 50) String trackCondition,
        @Size(max = 50) String weatherCondition,
        OffsetDateTime scheduledStartAt,
        OffsetDateTime predictionCutoffAt,
        @Positive Integer maxParticipants,
        RaceStatus status) {
}
