package com.SWP391.horserace.violations.service;

import com.SWP391.horserace.reports.entity.SeverityLevel;
import com.SWP391.horserace.violations.dto.CreateViolationRequest;
import com.SWP391.horserace.violations.dto.RulingRequest;
import com.SWP391.horserace.violations.dto.RulingResponse;
import com.SWP391.horserace.violations.dto.ViolationDetailResponse;
import com.SWP391.horserace.violations.dto.ViolationListItemResponse;
import com.SWP391.horserace.violations.entity.InfractionType;
import com.SWP391.horserace.violations.entity.ViolationStatus;

import java.util.List;
import java.util.UUID;

/** Violations / inquiries for a race + their official rulings (FE-v2 §3). */
public interface ViolationService {

    /** Log a new violation (status PENDING, reportedBy = caller). */
    ViolationDetailResponse createViolation(UUID currentUserId, UUID raceId, CreateViolationRequest request);

    /** All violations of a race, optionally filtered by status / severity / infractionType. */
    List<ViolationListItemResponse> listViolations(UUID raceId, ViolationStatus status,
                                                   SeverityLevel severity, InfractionType infractionType);

    /** Full detail of one violation, including any ruling. */
    ViolationDetailResponse getViolation(UUID violationId);

    /** Edit a violation's details (only while it has not yet been ruled). */
    ViolationDetailResponse updateViolation(UUID currentUserId, UUID violationId, CreateViolationRequest request);

    /** Delete a violation (referee/admin). */
    void deleteViolation(UUID currentUserId, UUID violationId);

    /** Record an official ruling; may create + link a penalty and resolve/dismiss the violation. */
    RulingResponse recordRuling(UUID currentUserId, UUID violationId, RulingRequest request);

    /** CSV export of a race's violations (raw CSV body, not wrapped in ApiResponse). */
    String exportCsv(UUID raceId);
}
