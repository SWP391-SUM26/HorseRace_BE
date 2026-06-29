package com.SWP391.horserace.violations.dto;

import com.SWP391.horserace.penalties.entity.PenaltyType;
import com.SWP391.horserace.violations.entity.ViolationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Result of PATCH /api/v1/violations/{id}/ruling. {@code penaltyId} is null unless a penalty was applied. */
@Data
@Builder
public class RulingResponse {
    private UUID violationId;
    private ViolationStatus status;
    private UUID penaltyId;
    private String decisionType;
    private PenaltyType penaltyType;
    private Long timePenaltyMs;
    private UUID ruledByUserId;
    private OffsetDateTime ruledAt;
}
