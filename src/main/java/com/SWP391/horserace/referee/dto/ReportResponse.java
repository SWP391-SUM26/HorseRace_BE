package com.SWP391.horserace.referee.dto;

import com.SWP391.horserace.reports.entity.ReportStatus;
import com.SWP391.horserace.reports.entity.ReportType;
import com.SWP391.horserace.reports.entity.SeverityLevel;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Referee report view (incident / violation / objection / general). */
@Data
@Builder
public class ReportResponse {
    private UUID reportId;
    private UUID raceId;
    private UUID authorUserId;
    private String authorName;
    private ReportType reportType;
    private String summary;
    private String decision;
    private SeverityLevel severityLevel;
    private ReportStatus reportStatus;
    private OffsetDateTime submittedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
