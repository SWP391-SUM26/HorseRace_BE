package com.SWP391.horserace.predictions.service;

import com.SWP391.horserace.predictions.dto.AdminPredictionResponse;
import com.SWP391.horserace.predictions.dto.PredictionFilterRequest;
import com.SWP391.horserace.predictions.dto.PredictionStatsResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

/** Admin-side moderation of predictions ("quản lý dự đoán kết quả"). */
public interface AdminPredictionService {

    Page<AdminPredictionResponse> list(PredictionFilterRequest filter);

    PredictionStatsResponse stats();

    /**
     * Void a still-PENDING bet and refund its stake. Unlike the bettor's own cancel this is not
     * limited to the betting window — an admin voids a bet precisely when something went wrong
     * outside the normal flow.
     */
    AdminPredictionResponse voidPrediction(UUID adminUserId, UUID predictionId, String reason);
}
