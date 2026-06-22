package com.SWP391.horserace.predictions.controller;

import com.SWP391.horserace.predictions.dto.PredictionRequest;
import com.SWP391.horserace.predictions.dto.PredictionResponse;
import com.SWP391.horserace.predictions.service.PredictionService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/predictions")
@RequiredArgsConstructor
public class PredictionController {

    private final PredictionService predictionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PredictionResponse> submitPrediction(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody PredictionRequest request) {

        return ApiResponse.<PredictionResponse>builder()
                .success(true)
                .message("Prediction submitted successfully")
                .data(predictionService.submitPrediction(userId, request))
                .build();
    }

    @GetMapping("/me")
    public ApiResponse<List<PredictionResponse>> getMyPredictionHistory(
            @AuthenticationPrincipal UUID userId) {

        return ApiResponse.<List<PredictionResponse>>builder()
                .success(true)
                .message("Fetched prediction history")
                .data(predictionService.getPredictionHistory(userId))
                .build();
    }

    @GetMapping("/me/{id}")
    public ApiResponse<PredictionResponse> getMyPredictionDetail(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {

        return ApiResponse.<PredictionResponse>builder()
                .success(true)
                .message("Fetched prediction detail")
                .data(predictionService.getPredictionDetail(userId, id))
                .build();
    }

    @PostMapping("/me/{id}/cancel")
    public ApiResponse<Void> cancelPrediction(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {

        predictionService.cancelPrediction(userId, id);
        return ApiResponse.<Void>builder()
                .success(true)
                .message("Prediction cancelled successfully")
                .build();
    }
}
