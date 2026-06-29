package com.SWP391.horserace.owner.controller;

import com.SWP391.horserace.horses.dto.HorseResponse;
import com.SWP391.horserace.owner.dto.OwnerOverviewResponse;
import com.SWP391.horserace.owner.service.OwnerService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/owner")
@RequiredArgsConstructor
public class OwnerController {

    private final OwnerService ownerService;

    /** GET /api/v1/owner/horses — the caller's own horses ("My Stable"). */
    @GetMapping("/horses")
    public ApiResponse<List<HorseResponse>> getOwnerHorses(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.<List<HorseResponse>>builder()
                .success(true)
                .message("Fetched owner horses")
                .data(ownerService.getOwnerHorses(userId))
                .build();
    }

    /** GET /api/v1/owner/overview — aggregated dashboard (KPIs, horses, upcoming races). */
    @GetMapping("/overview")
    public ApiResponse<OwnerOverviewResponse> getOverview(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.<OwnerOverviewResponse>builder()
                .success(true)
                .message("Fetched owner overview")
                .data(ownerService.getOverview(userId))
                .build();
    }

    /**
     * GET /api/v1/owner/races — IDs of every race the caller's horses are entered into
     * (any status, via their registrations). Powers the owner Race Calendar's "my races" filter.
     */
    @GetMapping("/races")
    public ApiResponse<List<UUID>> getOwnerRaceIds(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.<List<UUID>>builder()
                .success(true)
                .message("Fetched owner race ids")
                .data(ownerService.getOwnerRaceIds(userId))
                .build();
    }
}
