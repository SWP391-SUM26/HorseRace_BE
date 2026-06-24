package com.SWP391.horserace.races.dto;

/** Body for PATCH /api/v1/races/{raceId}/results/certify — flip results to OFFICIAL (FE-v2 mục 5). */
public record CertifyResultsRequest(
        String chiefStewardPin,
        Boolean acknowledgeInquiriesResolved,
        String stewardsReport
) {
}
