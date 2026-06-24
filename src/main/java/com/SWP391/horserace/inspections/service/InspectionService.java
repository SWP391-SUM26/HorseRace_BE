package com.SWP391.horserace.inspections.service;

import com.SWP391.horserace.inspections.dto.InspectionListItemResponse;
import com.SWP391.horserace.inspections.dto.InspectionRequest;
import com.SWP391.horserace.inspections.dto.InspectionResponse;
import com.SWP391.horserace.inspections.dto.SubmitAllRequest;
import com.SWP391.horserace.inspections.dto.SubmitAllResponse;
import com.SWP391.horserace.inspections.entity.InspectionStatus;

import java.util.List;
import java.util.UUID;

public interface InspectionService {

    /** Create or upsert the inspection for an entry, stamping the caller and the time. */
    InspectionResponse upsertInspection(UUID currentUserId, UUID raceId, InspectionRequest request);

    /** Every race entry merged with its inspection, optionally filtered by inspection status. */
    List<InspectionListItemResponse> listInspections(UUID raceId, InspectionStatus status);

    /** Submit all inspections; CLEARED entries pass, others are reported as blocked. */
    SubmitAllResponse submitAll(UUID raceId, SubmitAllRequest request);
}
