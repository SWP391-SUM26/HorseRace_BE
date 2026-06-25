package com.SWP391.horserace.prizes.repository;

import com.SWP391.horserace.prizes.entity.Prize;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PrizeRepository extends JpaRepository<Prize, UUID> {
}
