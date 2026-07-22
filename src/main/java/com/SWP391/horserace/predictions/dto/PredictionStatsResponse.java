package com.SWP391.horserace.predictions.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/** KPI row above the admin prediction table. */
@Data
@Builder
public class PredictionStatsResponse {
    private long total;
    private long pending;
    private long won;
    private long lost;
    private long voided;
    private BigDecimal totalStake;
    private BigDecimal totalPaidOut;
}
