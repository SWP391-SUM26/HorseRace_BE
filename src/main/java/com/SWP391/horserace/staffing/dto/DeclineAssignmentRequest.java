package com.SWP391.horserace.staffing.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Body for a referee declining a race assignment (CN1). Reason is optional. */
@Data
public class DeclineAssignmentRequest {

    @Size(max = 1000, message = "Reason must be at most 1000 characters")
    private String reason;
}
