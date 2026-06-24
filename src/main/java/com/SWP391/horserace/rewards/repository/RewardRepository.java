package com.SWP391.horserace.rewards.repository;

import com.SWP391.horserace.rewards.entity.Reward;
import com.SWP391.horserace.rewards.entity.RewardStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RewardRepository extends JpaRepository<Reward, UUID> {
    Page<Reward> findByUserUserIdAndStatus(UUID userId, RewardStatus status, Pageable pageable);
    Page<Reward> findByUserUserIdAndStatusNot(UUID userId, RewardStatus status, Pageable pageable);
}
