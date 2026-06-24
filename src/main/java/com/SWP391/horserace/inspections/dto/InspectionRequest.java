package com.SWP391.horserace.inspections.dto;

import com.SWP391.horserace.inspections.entity.InspectionStatus;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Body for POST /api/v1/races/{raceId}/inspections — create/upsert an entry's inspection. */
public record InspectionRequest(
        @NotNull UUID entryId,
        Boolean healthCertPassed,
        Boolean weightVerified,
        Integer weightCarriedLbs,
        Boolean cogginsTestPassed,
        Boolean preRaceExamPassed,
        InspectionStatus inspectionStatus,
        String stewardNote
) {
}
