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
import com.SWP391.horserace.wallets.entity.Wallet;
import com.SWP391.horserace.wallets.entity.WalletTransaction;
import com.SWP391.horserace.wallets.repository.WalletRepository;
import com.SWP391.horserace.wallets.repository.WalletTransactionRepository;
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
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
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

        // Add funds to wallet
        Wallet wallet = walletRepository.findByUserUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION)); // Assuming user always has a wallet

        wallet.setBalance(wallet.getBalance().add(reward.getAmount()));
        walletRepository.save(wallet);

        // Create transaction record
        WalletTransaction transaction = WalletTransaction.builder()
                .wallet(wallet)
                .entryType(EntryType.CREDIT)
                .txnCategory(TxnCategory.REWARD)
                .amount(reward.getAmount())
                .balanceAfter(wallet.getBalance())
                .relatedEntityType("REWARD")
                .relatedEntityId(reward.getRewardId())
                .build();
        walletTransactionRepository.save(transaction);

        // Update reward status
        reward.setStatus(RewardStatus.CLAIMED);
        reward.setClaimedAt(OffsetDateTime.now());
        rewardRepository.save(reward);

        log.info("User {} successfully claimed reward {}", userId, rewardId);

        return rewardMapper.toResponse(reward);
    }
}
