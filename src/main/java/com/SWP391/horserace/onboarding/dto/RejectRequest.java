package com.SWP391.horserace.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Body for PATCH /{id}/reject. */
@Data
public class RejectRequest {
    @NotBlank
    private String reason;
}
