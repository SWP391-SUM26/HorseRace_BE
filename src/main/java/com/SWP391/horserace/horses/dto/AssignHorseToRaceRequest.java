package com.SWP391.horserace.horses.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/** Assign a horse (via its approved registration) to a specific race. */
public record AssignHorseToRaceRequest(
        @NotNull UUID raceId,
        @Positive(message = "Lane number must be positive") Integer laneNo,
        @Positive(message = "Entry number must be positive") Integer entryNo
) {
}
