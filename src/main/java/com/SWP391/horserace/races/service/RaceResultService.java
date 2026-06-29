package com.SWP391.horserace.races.service;

import com.SWP391.horserace.races.dto.CertifyResultsRequest;
import com.SWP391.horserace.races.dto.CertifyResultsResponse;
import com.SWP391.horserace.races.dto.RaceResultsResponse;
import com.SWP391.horserace.races.dto.RecordResultsRequest;
import com.SWP391.horserace.races.dto.ResultRowResponse;
import com.SWP391.horserace.races.dto.UpdateResultRequest;
import com.SWP391.horserace.races.dto.UpdateResultResponse;

import java.util.List;
import java.util.UUID;

/** Results record/read/edit/certify for a race — FE-v2 §5. */
public interface RaceResultService {

    /** Bulk upsert the finish order (one race_result per entry, status PROVISIONAL). */
    List<ResultRowResponse> recordResults(UUID currentUserId, UUID raceId, RecordResultsRequest request);

    /** Read the full result sheet for a race, ordered by finish position. */
    RaceResultsResponse getResults(UUID raceId);

    /** Inline-edit one result row; snapshots the prior values into a new version (AMENDED). */
    UpdateResultResponse updateResult(UUID currentUserId, UUID raceId, UUID resultId, UpdateResultRequest request);

    /** Delete one result row (and its version history). Rejected once results are OFFICIAL. */
    void deleteResult(UUID currentUserId, UUID raceId, UUID resultId);

    /** Certify the race — flips all results + the race to OFFICIAL. */
    CertifyResultsResponse certify(UUID currentUserId, UUID raceId, CertifyResultsRequest request);
}
