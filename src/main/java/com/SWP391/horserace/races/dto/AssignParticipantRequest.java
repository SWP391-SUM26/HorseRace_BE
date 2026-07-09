package com.SWP391.horserace.races.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/** Body for POST /api/v1/races/{id}/entries — assign an approved registration to a race. */
public record AssignParticipantRequest(
        @NotNull UUID registrationId,
        @Positive(message = "Lane number must be positive") Integer laneNo,
        @Positive(message = "Entry number must be positive") Integer entryNo) {
}
