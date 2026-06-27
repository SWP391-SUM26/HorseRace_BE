package com.SWP391.horserace.races.controller;

import com.SWP391.horserace.races.dto.GlobalResultResponse;
import com.SWP391.horserace.races.dto.ResultFilterRequest;
import com.SWP391.horserace.races.service.ResultManagementService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/management/results")
@RequiredArgsConstructor
public class ResultManagementController {

    private final ResultManagementService resultManagementService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RACE_REFEREE')")
    public ApiResponse<Page<GlobalResultResponse>> getGlobalResults(
            @ParameterObject @ModelAttribute ResultFilterRequest filter,
            @ParameterObject Pageable pageable) {
        
        return ApiResponse.<Page<GlobalResultResponse>>builder()
                .success(true)
                .message("Fetched global results successfully")
                .data(resultManagementService.getGlobalResults(filter, pageable))
                .build();
    }
}
