package com.SWP391.horserace.violations.dto;

import com.SWP391.horserace.penalties.entity.PenaltyType;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * Body for PATCH /api/v1/violations/{id}/ruling — record an official ruling. When
 * {@code decisionType == "PENALTY_APPLIED"} a penalty row is created and linked.
 */
public record RulingRequest(
        @NotBlank String decisionType,
        PenaltyType penaltyType,
        Long timePenaltyMs,
        BigDecimal fineAmount,
        String rulingNotes
) {
}
