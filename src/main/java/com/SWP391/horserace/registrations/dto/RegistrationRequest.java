package com.SWP391.horserace.registrations.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Body for POST /api/v1/registrations — enter a horse into a tournament. */
public record RegistrationRequest(
        @NotNull UUID tournamentId,
        @NotNull UUID horseId,
        // Optional: the race the owner chooses to enter. When present it is validated against
        // the tournament; approval of this registration then auto-creates the race entry.
        UUID raceId) {
}
