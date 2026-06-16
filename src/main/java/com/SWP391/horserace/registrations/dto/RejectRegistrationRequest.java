package com.SWP391.horserace.registrations.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for PATCH /api/v1/registrations/{id}/reject. */
public record RejectRegistrationRequest(
        @NotBlank String reason) {
}
