package com.SWP391.horserace.referee.dto;

import com.SWP391.horserace.reports.entity.ReportType;
import com.SWP391.horserace.reports.entity.SeverityLevel;
import jakarta.validation.constraints.Size;

/** Body for PUT /api/v1/referee/reports/{id} — all fields optional (partial update). */
public record UpdateReportRequest(
        ReportType reportType,
        @Size(max = 5000) String summary,
        @Size(max = 5000) String decision,
        SeverityLevel severityLevel
) {
}
