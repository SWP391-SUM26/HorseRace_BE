package com.SWP391.horserace.prizes.controller;

import com.SWP391.horserace.prizes.dto.PrizeHistoryResponse;
import com.SWP391.horserace.prizes.service.PrizeService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/prizes")
@RequiredArgsConstructor
public class PrizeController {

    private final PrizeService prizeService;

    @GetMapping("/history")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Page<PrizeHistoryResponse>> getPrizeHistory(
            @org.springdoc.core.annotations.ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.<Page<PrizeHistoryResponse>>builder()
                .success(true)
                .message("Fetched prize history successfully")
                .data(prizeService.getPrizeHistory(pageable))
                .build();
    }
}
