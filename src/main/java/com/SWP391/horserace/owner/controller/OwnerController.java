package com.SWP391.horserace.owner.controller;

import com.SWP391.horserace.horses.dto.HorseResponse;
import com.SWP391.horserace.owner.dto.OwnerOverviewResponse;
import com.SWP391.horserace.owner.dto.OwnerRaceReportRow;
import com.SWP391.horserace.owner.service.OwnerService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * GET /api/v1/owner/race-report — per-race report rows: every race the caller registered for,
     * each horse annotated with whether it actually entered the race + its result.
     */
    @GetMapping("/race-report")
    public ApiResponse<List<OwnerRaceReportRow>> getRaceReport(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.<List<OwnerRaceReportRow>>builder()
                .success(true)
                .message("Fetched owner race report")
                .data(ownerService.getRaceReport(userId))
                .build();
    }

    /**
     * GET /api/v1/owner/finances — earnings, expenses and recent ledger for the caller.
     * Always scoped to the authenticated owner; there is no way to ask for someone else's books.
     */
    @GetMapping("/finances")
    @PreAuthorize("hasRole('HORSE_OWNER')")
    public ApiResponse<com.SWP391.horserace.owner.dto.OwnerFinanceResponse> getFinances(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(value = "txnLimit", defaultValue = "20") int txnLimit) {
        return ApiResponse.<com.SWP391.horserace.owner.dto.OwnerFinanceResponse>builder()
                .success(true)
                .message("Fetched owner finances")
                .data(ownerService.getFinances(userId, txnLimit))
                .build();
    }

    /**
     * GET /api/v1/owner/finances/races — per-race prize-won vs. jockey-fee-paid breakdown for the
     * caller, scoped to races that have reached OFFICIAL (prize + jockey fee are settled together
     * at certify time). Most recently settled race first.
     */
    @GetMapping("/finances/races")
    @PreAuthorize("hasRole('HORSE_OWNER')")
    public ApiResponse<List<com.SWP391.horserace.owner.dto.OwnerRaceEarningsRow>> getRaceEarnings(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return ApiResponse.<List<com.SWP391.horserace.owner.dto.OwnerRaceEarningsRow>>builder()
                .success(true)
                .message("Fetched owner race earnings")
                .data(ownerService.getRaceEarnings(userId, limit))
                .build();
    }
}
