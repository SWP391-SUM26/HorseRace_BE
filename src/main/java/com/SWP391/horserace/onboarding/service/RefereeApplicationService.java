package com.SWP391.horserace.onboarding.service;

import com.SWP391.horserace.onboarding.dto.ApplicationDetail;
import com.SWP391.horserace.onboarding.dto.ApplicationSummary;
import com.SWP391.horserace.onboarding.dto.OnboardingStatsResponse;
import com.SWP391.horserace.onboarding.entity.ApplicationStatus;
import com.SWP391.horserace.onboarding.entity.RequestedRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/** Referee applicant onboarding (Registration Approval) — see FE-v2 contract. */
public interface RefereeApplicationService {

    Page<ApplicationSummary> list(ApplicationStatus status, RequestedRole requestedRole, String q, Pageable pageable);

    OnboardingStatsResponse stats();

    ApplicationDetail getDetail(UUID applicationId);

    /** Approve & onboard: create/activate an app_user for the requested role. */
    ApplicationDetail approve(UUID applicationId, UUID reviewerUserId);

    ApplicationDetail reject(UUID applicationId, String reason, UUID reviewerUserId);

    ApplicationDetail requestInfo(UUID applicationId, String note, UUID reviewerUserId);

    /** Previous applications by the same applicant email (excludes the current one). */
    List<ApplicationSummary> history(UUID applicationId);
}
