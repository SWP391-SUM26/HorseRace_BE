package com.SWP391.horserace.races.dto;

import com.SWP391.horserace.races.entity.OfficialityStatus;
import com.SWP391.horserace.races.entity.RaceStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Body returned by PATCH /results/certify — certification outcome (FE-v2 mục 5). */
@Data
@Builder
public class CertifyResultsResponse {
    private UUID raceId;
    private RaceStatus raceStatus;
    private OfficialityStatus officialityStatus;
    private UUID certifiedByUserId;
    private String certifiedByName;
    private OffsetDateTime publishedAt;
    private int openInquiries;
}
