package com.SWP391.horserace.registrations.dto;

import lombok.Builder;
import lombok.Data;

/**
 * KPI aggregate for the registration management screen (FE-v2 §7).
 * pending = SUBMITTED + UNDER_REVIEW; approved = APPROVED; rejected = REJECTED; total = all statuses.
 */
@Data
@Builder
public class RegistrationStatsResponse {
    private long total;
    private long pending;
    private long approved;
    private long rejected;
}
