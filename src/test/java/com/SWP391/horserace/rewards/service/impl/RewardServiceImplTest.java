package com.SWP391.horserace.rewards.service.impl;

import com.SWP391.horserace.rewards.dto.RewardMapper;
import com.SWP391.horserace.rewards.dto.RewardResponse;
import com.SWP391.horserace.rewards.entity.Reward;
import com.SWP391.horserace.rewards.entity.RewardStatus;
import com.SWP391.horserace.rewards.repository.RewardRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.wallets.entity.EntryType;
import com.SWP391.horserace.wallets.entity.TxnCategory;
import com.SWP391.horserace.wallets.service.WalletLedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RewardServiceImplTest {

    @Mock RewardRepository rewardRepository;
    @Mock WalletLedgerService walletLedgerService;
    @Mock RewardMapper rewardMapper;

    private RewardServiceImpl service;

    private final UUID userId = UUID.randomUUID();
    private final UUID rewardId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RewardServiceImpl(rewardRepository, walletLedgerService, rewardMapper);
    }

    private Reward pendingReward() {
        return Reward.builder()
                .rewardId(rewardId)
                .user(User.builder().userId(userId).build())
                .amount(new BigDecimal("100000.00"))
                .status(RewardStatus.PENDING)
                .build();
    }

    @Test
    void claimReward_whenAtomicClaimWins_creditsWalletOnce() {
        Reward reward = pendingReward();
        when(rewardRepository.findById(rewardId)).thenReturn(Optional.of(reward));
        when(rewardRepository.markClaimedIfPending(eq(rewardId), any(OffsetDateTime.class))).thenReturn(1);
        when(rewardMapper.toResponse(any())).thenReturn(RewardResponse.builder().build());

        service.claimReward(rewardId, userId);

        verify(walletLedgerService).applyEntry(
                eq(userId), eq(EntryType.CREDIT), eq(TxnCategory.REWARD),
                eq(new BigDecimal("100000.00")), eq("REWARD"), eq(rewardId));
    }

    @Test
    void claimReward_whenAtomicClaimLosesRace_throwsAlreadyClaimed_andNeverCredits() {
        // Two concurrent claims both pass the in-memory PENDING check; the conditional UPDATE flips
        // the status exactly once, so the loser gets rowsAffected == 0 and must NOT credit again.
        Reward reward = pendingReward();
        when(rewardRepository.findById(rewardId)).thenReturn(Optional.of(reward));
        when(rewardRepository.markClaimedIfPending(eq(rewardId), any(OffsetDateTime.class))).thenReturn(0);

        assertThatThrownBy(() -> service.claimReward(rewardId, userId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.REWARD_ALREADY_CLAIMED);

        verify(walletLedgerService, never()).applyEntry(any(), any(), any(), any(), any(), any());
    }

    @Test
    void claimReward_rejectsWrongOwner_beforeClaiming() {
        Reward reward = pendingReward();
        when(rewardRepository.findById(rewardId)).thenReturn(Optional.of(reward));

        assertThatThrownBy(() -> service.claimReward(rewardId, UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_REWARD_OWNER);

        verify(rewardRepository, never()).markClaimedIfPending(any(), any());
        verify(walletLedgerService, never()).applyEntry(any(), any(), any(), any(), any(), any());
    }
}
