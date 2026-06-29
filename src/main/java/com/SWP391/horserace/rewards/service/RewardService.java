package com.SWP391.horserace.rewards.service;

import com.SWP391.horserace.rewards.dto.RewardResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface RewardService {
    Page<RewardResponse> getPendingRewards(UUID userId, Pageable pageable);
    
    Page<RewardResponse> getRewardHistory(UUID userId, Pageable pageable);
    
    RewardResponse claimReward(UUID rewardId, UUID userId);
}
