package com.SWP391.horserace.horses.dto;

import com.SWP391.horserace.horses.entity.HorseHealthStatus;
import jakarta.validation.constraints.Size;

/** Partial update of a horse's medical status; all fields optional. */
public record UpdateMedicalStatusRequest(
        HorseHealthStatus healthStatus,
        @Size(max = 2000) String medicalNote
) {
}
