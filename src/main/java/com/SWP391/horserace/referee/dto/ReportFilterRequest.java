package com.SWP391.horserace.referee.dto;

import com.SWP391.horserace.reports.entity.ReportStatus;
import com.SWP391.horserace.reports.entity.ReportType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Query params for GET /api/v1/referee/reports. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportFilterRequest {
    private UUID raceId;
    private ReportType reportType;
    private ReportStatus status;

    private String sortBy; // createdAt (default), submittedAt, severityLevel
    private String sortDir; // asc / desc

    @Builder.Default
    private Integer page = 0;

    @Builder.Default
    private Integer size = 10;
}
