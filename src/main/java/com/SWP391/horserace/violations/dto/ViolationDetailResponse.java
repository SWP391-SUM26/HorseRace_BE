package com.SWP391.horserace.violations.dto;

import com.SWP391.horserace.penalties.entity.PenaltyType;
import com.SWP391.horserace.reports.entity.SeverityLevel;
import com.SWP391.horserace.violations.entity.InfractionType;
import com.SWP391.horserace.violations.entity.ViolationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Full violation detail (GET /api/v1/violations/{id}) and the create response
 * (POST /api/v1/races/{raceId}/violations). {@code ruling} is null until a ruling is recorded.
 */
@Data
@Builder
public class ViolationDetailResponse {
    private UUID violationId;
    private UUID raceId;
    private UUID entryId;
    private String horseName;
    private String jockeyName;
    private InfractionType infractionType;
    private SeverityLevel severity;
    private Integer turnNo;
    private Long raceTimeOffsetMs;
    private String remarks;
    private String regulatoryRef;
    private String regulatoryText;
    private UUID footageAttachmentId;
    private String footageUrl;
    private ViolationStatus status;
    private UUID reportedByUserId;
    private OffsetDateTime createdAt;
    private Ruling ruling;

    /** Nested official-ruling block; null when no ruling has been recorded yet. */
    @Data
    @Builder
    public static class Ruling {
        private String decisionType;
        private PenaltyType penaltyType;
        /** Human-readable penalty value, e.g. "+2.0s" or "$500". */
        private String penaltyValue;
        private String ruledByName;
        private OffsetDateTime ruledAt;
    }
}
