package com.SWP391.horserace.registrations.controller;

import com.SWP391.horserace.registrations.dto.RegistrationFilterRequest;
import com.SWP391.horserace.registrations.dto.RegistrationRequest;
import com.SWP391.horserace.registrations.dto.RegistrationResponse;
import com.SWP391.horserace.registrations.dto.RegistrationStatsResponse;
import com.SWP391.horserace.registrations.dto.RejectRegistrationRequest;
import com.SWP391.horserace.registrations.service.RegistrationService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/registrations")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    /** POST /api/v1/registrations — enter a horse (owned by the caller) into a tournament. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegistrationResponse> submitRegistration(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody RegistrationRequest request) {
        return ApiResponse.<RegistrationResponse>builder()
                .success(true)
                .message("Registration submitted")
                .data(registrationService.submitRegistration(userId, request))
                .build();
    }

    /** GET /api/v1/registrations — list with search (q), filters, sort and pagination. */
    @GetMapping
    public ApiResponse<Page<RegistrationResponse>> listRegistrations(
            @ModelAttribute RegistrationFilterRequest filter) {
        return ApiResponse.<Page<RegistrationResponse>>builder()
                .success(true)
                .message("Fetched registrations")
                .data(registrationService.listRegistrations(filter))
                .build();
    }

    /** GET /api/v1/registrations/stats — KPI aggregate (FE-v2 §7), optionally scoped by tournament. */
    @GetMapping("/stats")
    public ApiResponse<RegistrationStatsResponse> getStats(
            @RequestParam(name = "tournamentId", required = false) UUID tournamentId) {
        return ApiResponse.<RegistrationStatsResponse>builder()
                .success(true)
                .message("Fetched registration stats")
                .data(registrationService.getStats(tournamentId))
                .build();
    }

    /** GET /api/v1/registrations/{id} — one registration. */
    @GetMapping("/{id}")
    public ApiResponse<RegistrationResponse> getRegistration(@PathVariable UUID id) {
        return ApiResponse.<RegistrationResponse>builder()
                .success(true)
                .message("Fetched registration")
                .data(registrationService.getRegistrationById(id))
                .build();
    }

    /** PATCH /api/v1/registrations/{id}/approve — referee accepts the registration (admin allowed too). */
    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('RACE_REFEREE','ADMIN')")
    public ApiResponse<RegistrationResponse> approveRegistration(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        return ApiResponse.<RegistrationResponse>builder()
                .success(true)
                .message("Registration approved")
                .data(registrationService.approveRegistration(userId, id))
                .build();
    }

    /** PATCH /api/v1/registrations/{id}/reject — referee rejects the registration (admin allowed too). */
    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('RACE_REFEREE','ADMIN')")
    public ApiResponse<RegistrationResponse> rejectRegistration(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody RejectRegistrationRequest request) {
        return ApiResponse.<RegistrationResponse>builder()
                .success(true)
                .message("Registration rejected")
                .data(registrationService.rejectRegistration(userId, id, request))
                .build();
    }

    /** PATCH /api/v1/registrations/{id}/withdraw — owner (or admin) withdraws a registration. */
    @PatchMapping("/{id}/withdraw")
    public ApiResponse<RegistrationResponse> withdrawRegistration(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        return ApiResponse.<RegistrationResponse>builder()
                .success(true)
                .message("Registration withdrawn")
                .data(registrationService.withdrawRegistration(userId, id))
                .build();
    }
}
