package com.SWP391.horserace.inspections.dto;

import com.SWP391.horserace.inspections.entity.InspectionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row of GET /api/v1/races/{raceId}/inspections — every race entry merged with its
 * inspection (inspectionId null + status PENDING when not yet inspected).
 */
@Data
@Builder
public class InspectionListItemResponse {
    private UUID inspectionId;
    private UUID entryId;
    private Integer laneNo;
    private UUID horseId;
    private String horseName;
    private String jockeyName;
    private boolean healthCertPassed;
    private boolean weightVerified;
    private InspectionStatus inspectionStatus;
    private OffsetDateTime inspectedAt;
}
