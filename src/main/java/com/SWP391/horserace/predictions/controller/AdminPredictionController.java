package com.SWP391.horserace.predictions.controller;

import com.SWP391.horserace.predictions.dto.AdminPredictionResponse;
import com.SWP391.horserace.predictions.dto.PredictionFilterRequest;
import com.SWP391.horserace.predictions.dto.PredictionStatsResponse;
import com.SWP391.horserace.predictions.service.AdminPredictionService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Admin moderation of predictions — "quản lý dự đoán kết quả".
 *
 * <p>{@code PredictionController} is entirely self-service ({@code /me} routes), so before this
 * there was no way for an admin to see or act on anyone else's bets.
 */
@RestController
@RequestMapping("/api/v1/admin/predictions")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminPredictionController {

    private final AdminPredictionService adminPredictionService;

    /** GET — every prediction, with search, filters, sort and pagination. */
    @GetMapping
    public ApiResponse<Page<AdminPredictionResponse>> list(@ModelAttribute PredictionFilterRequest filter) {
        return ApiResponse.<Page<AdminPredictionResponse>>builder()
                .success(true)
                .message("Fetched predictions")
                .data(adminPredictionService.list(filter))
                .build();
    }

    /** GET /stats — KPI aggregate for the moderation table header. */
    @GetMapping("/stats")
    public ApiResponse<PredictionStatsResponse> stats() {
        return ApiResponse.<PredictionStatsResponse>builder()
                .success(true)
                .message("Fetched prediction stats")
                .data(adminPredictionService.stats())
                .build();
    }

    /** PATCH /{id}/void — void an unsettled bet and refund the stake. */
    @PatchMapping("/{predictionId}/void")
    public ApiResponse<AdminPredictionResponse> voidPrediction(
            @AuthenticationPrincipal UUID adminUserId,
            @PathVariable UUID predictionId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ApiResponse.<AdminPredictionResponse>builder()
                .success(true)
                .message("Prediction voided and stake refunded")
                .data(adminPredictionService.voidPrediction(adminUserId, predictionId, reason))
                .build();
    }
}
