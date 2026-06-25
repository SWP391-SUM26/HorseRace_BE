package com.SWP391.horserace.financials.service.impl;

import com.SWP391.horserace.financials.dto.DailyTransactionSumProjection;
import com.SWP391.horserace.financials.dto.RevenueAnalyticsResponse;
import com.SWP391.horserace.financials.service.RevenueAnalyticsService;
import com.SWP391.horserace.wallets.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RevenueAnalyticsServiceImpl implements RevenueAnalyticsService {

    private final WalletTransactionRepository walletTransactionRepository;

    @Override
    @Transactional(readOnly = true)
    public List<RevenueAnalyticsResponse> getDailyRevenueAnalytics(LocalDate startDate, LocalDate endDate) {
        // Prepare timezone boundaries
        OffsetDateTime start = startDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = endDate.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        List<DailyTransactionSumProjection> sums = walletTransactionRepository.getDailyTransactionSums(start, end);

        // Group by Date
        Map<LocalDate, List<DailyTransactionSumProjection>> byDate = sums.stream()
                .collect(Collectors.groupingBy(DailyTransactionSumProjection::getTxnDate));

        List<RevenueAnalyticsResponse> results = new ArrayList<>();
        
        // Loop through the date range so we have 0 for missing days
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            List<DailyTransactionSumProjection> dailySums = byDate.getOrDefault(current, List.of());
            
            BigDecimal stakes = BigDecimal.ZERO;
            BigDecimal payouts = BigDecimal.ZERO;
            BigDecimal refunds = BigDecimal.ZERO;

            for (DailyTransactionSumProjection proj : dailySums) {
                if ("BET_STAKE".equals(proj.getTxnCategory())) {
                    stakes = proj.getTotalAmount();
                } else if ("BET_PAYOUT".equals(proj.getTxnCategory())) {
                    payouts = proj.getTotalAmount();
                } else if ("REFUND".equals(proj.getTxnCategory())) {
                    refunds = proj.getTotalAmount();
                }
            }

            BigDecimal revenue = stakes.subtract(payouts).subtract(refunds);

            results.add(RevenueAnalyticsResponse.builder()
                    .date(current)
                    .totalStakes(stakes)
                    .totalPayouts(payouts)
                    .totalRefunds(refunds)
                    .totalRevenue(revenue)
                    .build());
            
            current = current.plusDays(1);
        }

        return results;
    }
}
