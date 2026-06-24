package com.SWP391.horserace.tournaments.controller;

import com.SWP391.horserace.shared.dto.ApiResponse;
import com.SWP391.horserace.tournaments.dto.TournamentFilterRequest;
import com.SWP391.horserace.tournaments.dto.TournamentRequest;
import com.SWP391.horserace.tournaments.dto.TournamentResponse;
import com.SWP391.horserace.tournaments.service.TournamentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tournaments")
@RequiredArgsConstructor
public class TournamentController {

    private final TournamentService tournamentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<TournamentResponse> createTournament(
            @Valid @RequestBody TournamentRequest request,
            @RequestParam(value = "currentUserId", required = false) UUID currentUserIdParam,
            Authentication authentication) {
        
        UUID currentUserId = resolveUserId(authentication, currentUserIdParam);
        TournamentResponse response = tournamentService.createTournament(request, currentUserId);

        return ApiResponse.<TournamentResponse>builder()
                .success(true)
                .message("Tournament created successfully")
                .data(response)
                .build();
    }

    @GetMapping
    public ApiResponse<Page<TournamentResponse>> getTournaments(
            @ModelAttribute TournamentFilterRequest filter) {

        Page<TournamentResponse> page = tournamentService.getTournaments(filter);

        return ApiResponse.<Page<TournamentResponse>>builder()
                .success(true)
                .message("Fetched tournaments")
                .data(page)
                .build();
    }

    @GetMapping("/{id}")
    public ApiResponse<TournamentResponse> getTournamentById(@PathVariable UUID id) {
        TournamentResponse response = tournamentService.getTournamentById(id);

        return ApiResponse.<TournamentResponse>builder()
                .success(true)
                .message("Fetched tournament details")
                .data(response)
                .build();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<TournamentResponse> updateTournament(
            @PathVariable UUID id,
            @Valid @RequestBody TournamentRequest request) {

        TournamentResponse response = tournamentService.updateTournament(id, request);

        return ApiResponse.<TournamentResponse>builder()
                .success(true)
                .message("Tournament updated successfully")
                .data(response)
                .build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteTournament(@PathVariable UUID id) {
        tournamentService.deleteTournament(id);

        return ApiResponse.<Void>builder()
                .success(true)
                .message("Tournament deleted successfully")
                .build();
    }

    @PatchMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<TournamentResponse> publishTournament(@PathVariable UUID id) {
        TournamentResponse response = tournamentService.publishTournament(id);

        return ApiResponse.<TournamentResponse>builder()
                .success(true)
                .message("Tournament published successfully")
                .data(response)
                .build();
    }

    @PatchMapping("/{id}/close-registration")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<TournamentResponse> closeRegistration(@PathVariable UUID id) {
        TournamentResponse response = tournamentService.closeRegistration(id);

        return ApiResponse.<TournamentResponse>builder()
                .success(true)
                .message("Tournament registration closed successfully")
                .data(response)
                .build();
    }

    @RequestMapping(value = "/{id}/open-registration", method = {RequestMethod.POST, RequestMethod.PATCH})
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<TournamentResponse> openRegistration(@PathVariable UUID id) {
        return ApiResponse.<TournamentResponse>builder()
                .success(true)
                .message("Tournament registration opened successfully")
                .data(tournamentService.openRegistration(id))
                .build();
    }

    @RequestMapping(value = "/{id}/start", method = {RequestMethod.POST, RequestMethod.PATCH})
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<TournamentResponse> startTournament(@PathVariable UUID id) {
        return ApiResponse.<TournamentResponse>builder()
                .success(true)
                .message("Tournament started successfully")
                .data(tournamentService.startTournament(id))
                .build();
    }

    @RequestMapping(value = "/{id}/complete", method = {RequestMethod.POST, RequestMethod.PATCH})
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<TournamentResponse> completeTournament(@PathVariable UUID id) {
        return ApiResponse.<TournamentResponse>builder()
                .success(true)
                .message("Tournament completed successfully")
                .data(tournamentService.completeTournament(id))
                .build();
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private UUID resolveUserId(Authentication authentication, UUID fallbackUserId) {
        if (authentication != null && authentication.getName() != null
                && !"anonymousUser".equals(authentication.getName())) {
            try {
                return UUID.fromString(authentication.getName());
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (fallbackUserId != null) {
            return fallbackUserId;
        }
        return null;
    }
}
