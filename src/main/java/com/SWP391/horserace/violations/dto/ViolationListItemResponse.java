package com.SWP391.horserace.violations.dto;

import com.SWP391.horserace.reports.entity.SeverityLevel;
import com.SWP391.horserace.violations.entity.InfractionType;
import com.SWP391.horserace.violations.entity.ViolationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row of GET /api/v1/races/{raceId}/violations. {@code entityLabel} is
 * "Race {raceCode} / {horseName} / {jockeyName}" with missing parts omitted.
 */
@Data
@Builder
public class ViolationListItemResponse {
    private UUID violationId;
    private String entityLabel;
    private InfractionType infractionType;
    private SeverityLevel severity;
    private Integer turnNo;
    private Long raceTimeOffsetMs;
    private ViolationStatus status;
    private OffsetDateTime createdAt;
}
