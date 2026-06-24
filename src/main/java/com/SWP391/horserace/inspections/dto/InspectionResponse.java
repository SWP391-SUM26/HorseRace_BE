package com.SWP391.horserace.inspections.dto;

import com.SWP391.horserace.inspections.entity.InspectionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Full inspection returned by POST /api/v1/races/{raceId}/inspections. */
@Data
@Builder
public class InspectionResponse {
    private UUID inspectionId;
    private UUID entryId;
    private UUID raceId;
    private UUID horseId;
    private String horseName;
    private Integer laneNo;
    private boolean healthCertPassed;
    private boolean weightVerified;
    private Integer weightCarriedLbs;
    private boolean cogginsTestPassed;
    private boolean preRaceExamPassed;
    private InspectionStatus inspectionStatus;
    private String stewardNote;
    private UUID inspectedByUserId;
    private String inspectedByName;
    private OffsetDateTime inspectedAt;
}
