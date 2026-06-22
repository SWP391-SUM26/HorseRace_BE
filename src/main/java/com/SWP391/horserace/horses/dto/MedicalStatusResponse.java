package com.SWP391.horserace.horses.dto;

import com.SWP391.horserace.horses.entity.HorseHealthStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Current medical status of a horse (status model on the horse row). */
@Data
@Builder
public class MedicalStatusResponse {
    private UUID horseId;
    private String horseName;
    private HorseHealthStatus healthStatus;
    private OffsetDateTime lastHealthCheckAt;
    private String medicalNote;
}
