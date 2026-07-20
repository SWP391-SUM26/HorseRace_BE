package com.SWP391.horserace.rewards.repository;

import com.SWP391.horserace.rewards.entity.Reward;
import com.SWP391.horserace.rewards.entity.RewardStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface RewardRepository extends JpaRepository<Reward, UUID> {
    Page<Reward> findByUserUserIdAndStatus(UUID userId, RewardStatus status, Pageable pageable);
    Page<Reward> findByUserUserIdAndStatusNot(UUID userId, RewardStatus status, Pageable pageable);

    /**
     * Atomically claim a reward exactly once: flips PENDING → CLAIMED in a single conditional
     * UPDATE so two concurrent claims can't both credit the wallet. The caller credits only when
     * this returns 1 (rows affected). Mirrors {@code PaymentTransactionRepository.markSuccessIfPending}.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Reward r SET r.status = com.SWP391.horserace.rewards.entity.RewardStatus.CLAIMED, "
            + "r.claimedAt = :now WHERE r.rewardId = :rewardId "
            + "AND r.status = com.SWP391.horserace.rewards.entity.RewardStatus.PENDING")
    int markClaimedIfPending(@Param("rewardId") UUID rewardId, @Param("now") OffsetDateTime now);
}
