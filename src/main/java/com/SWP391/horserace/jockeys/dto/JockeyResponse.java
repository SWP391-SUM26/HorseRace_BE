package com.SWP391.horserace.jockeys.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * API view of a jockey profile. Combines professional info from {@code jockey_profile}
 * with basic user info from {@code app_user} so the client gets everything in one response.
 */
@Data
@Builder
public class JockeyResponse {

    // -- from app_user --
    private UUID userId;
    private String userCode;
    private String fullName;
    private String email;
    private String phone;
    private String avatarUrl;
    private String status;

    // -- from jockey_profile --
    private String licenseNo;
    private BigDecimal bodyWeight;
    private BigDecimal heightCm;
    private Integer experienceYrs;
    private Integer winCount;
    private String bio;
    private OffsetDateTime createdAt;
}
