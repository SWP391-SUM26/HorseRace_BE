package com.SWP391.horserace.financials.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialDashboardResponse {
    private BigDecimal totalSystemBalance;
    private BigDecimal totalLockedBalance;
    private BigDecimal totalDeposits;
    private BigDecimal totalWithdrawals;
    private BigDecimal totalBetStakes;
    private BigDecimal totalBetPayouts;
    private BigDecimal totalPrizes;
    private BigDecimal totalRefunds;
}
