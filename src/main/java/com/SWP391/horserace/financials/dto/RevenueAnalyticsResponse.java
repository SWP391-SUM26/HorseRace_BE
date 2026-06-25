package com.SWP391.horserace.financials.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueAnalyticsResponse {
    private LocalDate date;
    private BigDecimal totalStakes;
    private BigDecimal totalPayouts;
    private BigDecimal totalRefunds;
    private BigDecimal totalRevenue;
}
