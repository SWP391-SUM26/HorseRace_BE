package com.SWP391.horserace.horses.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Assign a horse (via its approved registration) to a specific race. */
public record AssignHorseToRaceRequest(
        @NotNull UUID raceId,
        Integer laneNo,
        Integer entryNo
) {
}
