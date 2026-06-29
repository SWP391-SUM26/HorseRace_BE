package com.SWP391.horserace.rewards.dto;

import com.SWP391.horserace.rewards.entity.Reward;
import org.springframework.stereotype.Component;

@Component
public class RewardMapper {

    public RewardResponse toResponse(Reward reward) {
        if (reward == null) {
            return null;
        }

        return RewardResponse.builder()
                .rewardId(reward.getRewardId())
                .rewardType(reward.getRewardType())
                .amount(reward.getAmount())
                .title(reward.getTitle())
                .description(reward.getDescription())
                .status(reward.getStatus())
                .expiresAt(reward.getExpiresAt())
                .claimedAt(reward.getClaimedAt())
                .createdAt(reward.getCreatedAt())
                .build();
    }
}
