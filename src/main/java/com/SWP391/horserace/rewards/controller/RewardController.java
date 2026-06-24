package com.SWP391.horserace.rewards.controller;

import com.SWP391.horserace.rewards.dto.RewardResponse;
import com.SWP391.horserace.rewards.service.RewardService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rewards")
@RequiredArgsConstructor
public class RewardController {

    private final RewardService rewardService;

    /**
     * GET /api/v1/rewards/notifications
     * Returns a paginated list of PENDING rewards for the authenticated user.
     * Functions as the user's reward notifications.
     */
    @GetMapping("/notifications")
    public ApiResponse<Page<RewardResponse>> getPendingRewards(
            @AuthenticationPrincipal UUID userId,
            Pageable pageable) {
        return ApiResponse.<Page<RewardResponse>>builder()
                .success(true)
                .message("Fetched pending rewards (notifications)")
                .data(rewardService.getPendingRewards(userId, pageable))
                .build();
    }

    /**
     * GET /api/v1/rewards/history
     * Returns a paginated list of CLAIMED or EXPIRED rewards for the authenticated user.
     */
    @GetMapping("/history")
    public ApiResponse<Page<RewardResponse>> getRewardHistory(
            @AuthenticationPrincipal UUID userId,
            Pageable pageable) {
        return ApiResponse.<Page<RewardResponse>>builder()
                .success(true)
                .message("Fetched reward history")
                .data(rewardService.getRewardHistory(userId, pageable))
                .build();
    }

    /**
     * POST /api/v1/rewards/{rewardId}/claim
     * Claims a pending reward, adding the amount to the user's wallet.
     */
    @PostMapping("/{rewardId}/claim")
    public ApiResponse<RewardResponse> claimReward(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID rewardId) {
        return ApiResponse.<RewardResponse>builder()
                .success(true)
                .message("Reward claimed successfully")
                .data(rewardService.claimReward(rewardId, userId))
                .build();
    }
}
