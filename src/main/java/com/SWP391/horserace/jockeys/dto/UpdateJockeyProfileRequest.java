package com.SWP391.horserace.jockeys.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Body for {@code PUT /api/v1/jockeys/me} (FE-v2 jockey contract #8).
 *
 * <p>All fields are optional — this is a partial update. Only non-null fields are
 * applied to the caller's own {@code jockey_profile}.
 */
@Data
public class UpdateJockeyProfileRequest {
    private BigDecimal bodyWeight;
    private BigDecimal heightCm;
    private String ridingStyle;
    private String bio;
    private String licenseNo;
    private BigDecimal baseFee;
    private BigDecimal prizePercent;
}
