package com.SWP391.horserace.races.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/** Body for PATCH /api/v1/races/{id}/schedule. */
public record ScheduleRaceRequest(
        @NotNull OffsetDateTime scheduledStartAt,
        OffsetDateTime predictionCutoffAt) {
}
