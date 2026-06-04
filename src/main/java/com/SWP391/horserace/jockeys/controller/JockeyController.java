package com.SWP391.horserace.jockeys.controller;

import com.SWP391.horserace.jockeys.dto.JockeyResponse;
import com.SWP391.horserace.jockeys.service.JockeyService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jockeys")
@RequiredArgsConstructor
public class JockeyController {

    private final JockeyService jockeyService;

    /** GET /api/v1/jockeys — list all active jockeys. */
    @GetMapping
    public ApiResponse<List<JockeyResponse>> getAllJockeys() {
        return ApiResponse.<List<JockeyResponse>>builder()
                .success(true)
                .message("Fetched all jockeys")
                .data(jockeyService.getAllJockeys())
                .build();
    }

    /** GET /api/v1/jockeys/{id} — single jockey by user UUID. */
    @GetMapping("/{id}")
    public ApiResponse<JockeyResponse> getJockeyById(@PathVariable UUID id) {
        return ApiResponse.<JockeyResponse>builder()
                .success(true)
                .message("Fetched jockey")
                .data(jockeyService.getJockeyById(id))
                .build();
    }
}
