package com.SWP391.horserace.onboarding.dto;

import lombok.Builder;
import lombok.Data;

/** Stats cards for the Registration Approval dashboard (today-scoped on reviewed_at). */
@Data
@Builder
public class OnboardingStatsResponse {
    private long pendingApprovals;
    private long approvedToday;
    private long rejectedToday;
}
