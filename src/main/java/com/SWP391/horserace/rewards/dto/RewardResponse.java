package com.SWP391.horserace.rewards.dto;

import com.SWP391.horserace.rewards.entity.RewardStatus;
import com.SWP391.horserace.rewards.entity.RewardType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class RewardResponse {
    private UUID rewardId;
    private RewardType rewardType;
    private BigDecimal amount;
    private String title;
    private String description;
    private RewardStatus status;
    private OffsetDateTime expiresAt;
    private OffsetDateTime claimedAt;
    private OffsetDateTime createdAt;
}
