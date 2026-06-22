package com.SWP391.horserace.referee.dto;

import com.SWP391.horserace.horses.entity.HorseHealthStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body for POST /api/v1/referee/horses/{horseId}/health-check. */
public record HealthCheckRequest(
        @NotNull HorseHealthStatus healthStatus,
        @Size(max = 2000) String note
) {
}
