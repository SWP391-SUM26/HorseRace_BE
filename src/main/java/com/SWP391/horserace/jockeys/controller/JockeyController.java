package com.SWP391.horserace.jockeys.controller;

import com.SWP391.horserace.jockeys.dto.InvitationInsightsResponse;
import com.SWP391.horserace.jockeys.dto.JockeyFilterRequest;
import com.SWP391.horserace.jockeys.dto.JockeyResponse;
import com.SWP391.horserace.jockeys.dto.JockeyStatsResponse;
import com.SWP391.horserace.jockeys.dto.UpdateJockeyProfileRequest;
import com.SWP391.horserace.jockeys.service.JockeyService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /**
     * GET /api/v1/jockeys/page — paginated listing of jockeys with sorting.
     *
     * <p>Query parameters:
     * <ul>
     *   <li>{@code page}    — page number (0-indexed, default 0)</li>
     *   <li>{@code size}    — page size (default 10)</li>
     *   <li>{@code sortBy}  — winCount (default), experienceYrs, bodyWeight, heightCm, fullName</li>
     *   <li>{@code sortDir} — asc / desc (default: desc)</li>
     * </ul>
     */
    @GetMapping("/page")
    public ApiResponse<Page<JockeyResponse>> getJockeysPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "winCount") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ApiResponse.<Page<JockeyResponse>>builder()
                .success(true)
                .message("Paginated jockeys")
                .data(jockeyService.getJockeysPaginated(page, size, sortBy, sortDir))
                .build();
    }

    /**
     * PUT /api/v1/jockeys/me — partial update of the caller's own jockey profile
     * (FE-v2 jockey contract #8). Caller resolved from the JWT principal.
     */
    @PutMapping("/me")
    public ApiResponse<JockeyResponse> updateMyProfile(
            @AuthenticationPrincipal UUID userId,
            @RequestBody UpdateJockeyProfileRequest request) {
        return ApiResponse.<JockeyResponse>builder()
                .success(true)
                .message("Profile updated")
                .data(jockeyService.updateMyProfile(userId, request))
                .build();
    }

    /** GET /api/v1/jockeys/me/stats — aggregated performance + earnings (FE-v2 jockey contract #1). */
    @GetMapping("/me/stats")
    public ApiResponse<JockeyStatsResponse> getMyStats(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.<JockeyStatsResponse>builder()
                .success(true)
                .message("Fetched jockey stats")
                .data(jockeyService.getMyStats(userId))
                .build();
    }

    /** GET /api/v1/jockeys/me/invitation-insights — invitation analytics (FE-v2 jockey contract #11). */
    @GetMapping("/me/invitation-insights")
    public ApiResponse<InvitationInsightsResponse> getMyInvitationInsights(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.<InvitationInsightsResponse>builder()
                .success(true)
                .message("Fetched invitation insights")
                .data(jockeyService.getMyInvitationInsights(userId))
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

