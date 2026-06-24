package com.SWP391.horserace.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Body for PATCH /{id}/request-info. */
@Data
public class RequestInfoRequest {
    @NotBlank
    private String note;
}
