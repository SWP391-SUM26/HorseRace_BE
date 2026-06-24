package com.SWP391.horserace.onboarding.dto;

import com.SWP391.horserace.onboarding.entity.ApplicationPriority;
import com.SWP391.horserace.onboarding.entity.ApplicationStatus;
import com.SWP391.horserace.onboarding.entity.RequestedRole;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Queue row for the Registration Approval list. */
@Data
@Builder
public class ApplicationSummary {
    private UUID applicationId;
    private String applicationCode;
    private String fullName;
    private RequestedRole requestedRole;
    private ApplicationPriority priority;
    private ApplicationStatus status;
    private OffsetDateTime submittedAt;
}
