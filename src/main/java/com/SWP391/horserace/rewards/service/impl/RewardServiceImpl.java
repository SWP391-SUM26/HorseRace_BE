package com.SWP391.horserace.rewards.service.impl;

import com.SWP391.horserace.rewards.dto.RewardMapper;
import com.SWP391.horserace.rewards.dto.RewardResponse;
import com.SWP391.horserace.rewards.entity.Reward;
import com.SWP391.horserace.rewards.entity.RewardStatus;
import com.SWP391.horserace.rewards.repository.RewardRepository;
import com.SWP391.horserace.rewards.service.RewardService;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.wallets.entity.EntryType;
import com.SWP391.horserace.wallets.entity.TxnCategory;
import com.SWP391.horserace.wallets.service.WalletLedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RewardServiceImpl implements RewardService {

    private final RewardRepository rewardRepository;
    private final WalletLedgerService walletLedgerService;
    private final RewardMapper rewardMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<RewardResponse> getPendingRewards(UUID userId, Pageable pageable) {
        Page<Reward> rewards = rewardRepository.findByUserUserIdAndStatus(userId, RewardStatus.PENDING, pageable);
        return rewards.map(rewardMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RewardResponse> getRewardHistory(UUID userId, Pageable pageable) {
        // Exclude pending rewards to get the history (claimed or expired)
        Page<Reward> rewards = rewardRepository.findByUserUserIdAndStatusNot(userId, RewardStatus.PENDING, pageable);
        return rewards.map(rewardMapper::toResponse);
    }

    @Override
    @Transactional
    public RewardResponse claimReward(UUID rewardId, UUID userId) {
        Reward reward = rewardRepository.findById(rewardId)
                .orElseThrow(() -> new AppException(ErrorCode.REWARD_NOT_FOUND));

        if (!reward.getUser().getUserId().equals(userId)) {
            throw new AppException(ErrorCode.NOT_REWARD_OWNER);
        }

        if (reward.getStatus() == RewardStatus.CLAIMED) {
            throw new AppException(ErrorCode.REWARD_ALREADY_CLAIMED);
        }

        if (reward.getStatus() == RewardStatus.EXPIRED || 
            (reward.getExpiresAt() != null && reward.getExpiresAt().isBefore(OffsetDateTime.now()))) {
            reward.setStatus(RewardStatus.EXPIRED);
            rewardRepository.save(reward);
            throw new AppException(ErrorCode.REWARD_EXPIRED);
        }

        if (reward.getStatus() != RewardStatus.PENDING) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION); // Edge case protection
        }

        // Atomically claim the reward BEFORE crediting: a single conditional UPDATE flips
        // PENDING → CLAIMED, so two concurrent claims can't both pass the status check above and
        // both credit the wallet (the in-memory check is TOCTOU under READ COMMITTED). Only the
        // caller that wins the race (rowsAffected == 1) proceeds to credit; the loser gets 0.
        OffsetDateTime claimedAt = OffsetDateTime.now();
        int claimed = rewardRepository.markClaimedIfPending(rewardId, claimedAt);
        if (claimed == 0) {
            throw new AppException(ErrorCode.REWARD_ALREADY_CLAIMED);
        }

        // Credit through the row-locked, idempotent wallet ledger (WALLET_NOT_FOUND if absent).
        walletLedgerService.applyEntry(
                userId, EntryType.CREDIT, TxnCategory.REWARD,
                reward.getAmount(), "REWARD", reward.getRewardId());

        // Reflect the committed status transition on the returned entity (the UPDATE bypassed the
        // persistence context, and @Modifying(clearAutomatically) evicted it).
        reward.setStatus(RewardStatus.CLAIMED);
        reward.setClaimedAt(claimedAt);

        log.info("User {} successfully claimed reward {}", userId, rewardId);

        return rewardMapper.toResponse(reward);
    }
}
