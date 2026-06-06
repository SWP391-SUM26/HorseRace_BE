package com.SWP391.horserace.jockeys.controller;

import com.SWP391.horserace.jockeys.dto.JockeyFilterRequest;
import com.SWP391.horserace.jockeys.dto.JockeyResponse;
import com.SWP391.horserace.jockeys.service.JockeyService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * GET /api/v1/jockeys/filter — filter jockeys by multiple optional criteria.
     *
     * <p>Supported query parameters:
     * <ul>
     *   <li>{@code fullName}       — partial, case-insensitive name match</li>
     *   <li>{@code email}          — partial, case-insensitive email match</li>
     *   <li>{@code status}         — exact user status (ACTIVE, INACTIVE, SUSPENDED, BANNED)</li>
     *   <li>{@code licenseNo}      — partial, case-insensitive license number match</li>
     *   <li>{@code minExperienceYrs / maxExperienceYrs} — experience range</li>
     *   <li>{@code minBodyWeight / maxBodyWeight}       — weight range (kg)</li>
     *   <li>{@code minHeightCm / maxHeightCm}           — height range (cm)</li>
     *   <li>{@code minWinCount / maxWinCount}           — win count range</li>
     *   <li>{@code sortBy}         — winCount (default), experienceYrs, bodyWeight, heightCm, fullName</li>
     *   <li>{@code sortDir}        — asc / desc (default: desc)</li>
     * </ul>
     */
    @GetMapping("/filter")
    public ApiResponse<List<JockeyResponse>> filterJockeys(@ModelAttribute JockeyFilterRequest filter) {
        return ApiResponse.<List<JockeyResponse>>builder()
                .success(true)
                .message("Filtered jockeys")
                .data(jockeyService.filterJockeys(filter))
                .build();
    }

    /** GET /api/v1/jockeys/search?keyword=... — search jockeys by name, email, license, or code. */
    @GetMapping("/search")
    public ApiResponse<List<JockeyResponse>> searchJockeys(
            @RequestParam(value = "keyword", required = false, defaultValue = "") String keyword) {
        return ApiResponse.<List<JockeyResponse>>builder()
                .success(true)
                .message("Search results")
                .data(jockeyService.searchJockeys(keyword))
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

