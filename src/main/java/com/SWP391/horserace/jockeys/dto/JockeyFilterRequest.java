package com.SWP391.horserace.jockeys.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Query-parameter DTO for {@code GET /api/v1/jockeys/filter}.
 * Every field is optional — omitted fields are not included in the filter.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JockeyFilterRequest {

    // -- from app_user --
    private String fullName;        // partial, case-insensitive
    private String email;           // partial, case-insensitive
    private String status;          // exact match (ACTIVE, INACTIVE, SUSPENDED, BANNED)

    // -- from jockey_profile --
    private String licenseNo;       // partial, case-insensitive
    private Integer minExperienceYrs;
    private Integer maxExperienceYrs;
    private BigDecimal minBodyWeight;
    private BigDecimal maxBodyWeight;
    private BigDecimal minHeightCm;
    private BigDecimal maxHeightCm;
    private Integer minWinCount;
    private Integer maxWinCount;

    // -- sorting --
    private String sortBy;          // winCount (default), experienceYrs, bodyWeight, heightCm, fullName
    private String sortDir;         // asc / desc (default: desc)
}
