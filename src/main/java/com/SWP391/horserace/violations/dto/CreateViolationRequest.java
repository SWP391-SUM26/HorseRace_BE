package com.SWP391.horserace.violations.dto;

import com.SWP391.horserace.reports.entity.SeverityLevel;
import com.SWP391.horserace.violations.entity.InfractionType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Body for POST /api/v1/races/{raceId}/violations — log a new violation (status PENDING). */
public record CreateViolationRequest(
        UUID entryId,
        @NotNull InfractionType infractionType,
        SeverityLevel severity,
        Integer turnNo,
        Long raceTimeOffsetMs,
        String remarks,
        String regulatoryRef,
        UUID footageAttachmentId,
        /** The referee's per-race code (admin-issued); required for referees, ignored for admins. */
        String refCode
) {
}
