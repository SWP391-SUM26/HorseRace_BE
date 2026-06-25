package com.SWP391.horserace.financials.controller;

import com.SWP391.horserace.financials.dto.FinancialDashboardResponse;
import com.SWP391.horserace.financials.service.FinancialDashboardService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/financials")
@RequiredArgsConstructor
public class FinancialDashboardController {

    private final FinancialDashboardService financialDashboardService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<FinancialDashboardResponse> getDashboard() {
        return ApiResponse.<FinancialDashboardResponse>builder()
                .success(true)
                .message("Fetched financial dashboard successfully")
                .data(financialDashboardService.getDashboard())
                .build();
    }
}
