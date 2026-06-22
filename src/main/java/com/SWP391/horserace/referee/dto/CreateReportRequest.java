package com.SWP391.horserace.referee.dto;

import com.SWP391.horserace.reports.entity.ReportType;
import com.SWP391.horserace.reports.entity.SeverityLevel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Body for POST /api/v1/referee/reports. */
public record CreateReportRequest(
        @NotNull UUID raceId,
        ReportType reportType,
        @Size(max = 5000) String summary,
        @Size(max = 5000) String decision,
        SeverityLevel severityLevel
) {
}
