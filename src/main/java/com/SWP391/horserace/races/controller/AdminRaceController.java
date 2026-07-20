package com.SWP391.horserace.races.controller;

import com.SWP391.horserace.predictions.service.SettlementService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Admin recovery operations on a race (settlement retry). */
@RestController
@RequestMapping("/api/v1/admin/races")
@RequiredArgsConstructor
public class AdminRaceController {

    private final SettlementService settlementService;

    /**
     * POST /{raceId}/resettle — manually re-run the idempotent settlement for a race left with
     * unsettled pools (e.g. the process died between certify commit and settlement finish). A safe
     * no-op on a fully-settled race; drains only the remaining pools on a partially-settled one.
     */
    @PostMapping("/{raceId}/resettle")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> resettle(@PathVariable UUID raceId) {
        settlementService.settleRace(raceId);
        return ApiResponse.<Void>builder()
                .success(true)
                .message("Race resettled")
                .build();
    }
}
