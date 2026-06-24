package com.SWP391.horserace.races.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Request body for PATCH /api/v1/races/{raceId}/live
 * Used to update the live positions and speeds of horses during a race.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLivePositionRequest {

    @NotNull(message = "Runner telemetry list cannot be null")
    @Valid
    private List<RunnerTelemetry> runners;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RunnerTelemetry {
        @NotNull(message = "Entry ID is required")
        private UUID entryId;

        /** The current running position in the race. */
        private Integer position;

        /** The current speed of the horse in KPH. */
        private BigDecimal currentSpeedKph;
    }
}
