package com.SWP391.horserace.financials.service;

import com.SWP391.horserace.financials.dto.RevenueAnalyticsResponse;
import java.time.LocalDate;
import java.util.List;

public interface RevenueAnalyticsService {
    List<RevenueAnalyticsResponse> getDailyRevenueAnalytics(LocalDate startDate, LocalDate endDate);
}
