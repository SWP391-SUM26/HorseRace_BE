package com.SWP391.horserace.races.controller;

import com.SWP391.horserace.races.dto.AssignParticipantRequest;
import com.SWP391.horserace.races.dto.MyEntryResponse;
import com.SWP391.horserace.races.dto.RaceEntryResponse;
import com.SWP391.horserace.races.dto.RaceFilterRequest;
import com.SWP391.horserace.races.dto.RaceRequest;
import com.SWP391.horserace.races.dto.RaceResponse;
import com.SWP391.horserace.races.dto.ScheduleRaceRequest;
import com.SWP391.horserace.races.service.RaceService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/races")
@RequiredArgsConstructor
public class RaceController {

    private final RaceService raceService;

    /** GET /api/v1/races — list with search (q), filters, sort and pagination. */
    @GetMapping
    public ApiResponse<Page<RaceResponse>> listRaces(@ModelAttribute RaceFilterRequest filter) {
        return ApiResponse.<Page<RaceResponse>>builder()
                .success(true)
                .message("Fetched races")
                .data(raceService.listRaces(filter))
                .build();
    }

    /** GET /api/v1/races/{id} — one race. */
    @GetMapping("/{id}")
    public ApiResponse<RaceResponse> getRace(@PathVariable UUID id) {
        return ApiResponse.<RaceResponse>builder()
                .success(true)
                .message("Fetched race")
                .data(raceService.getRaceById(id))
                .build();
    }

    /** POST /api/v1/races — create a race under a tournament. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RaceResponse> createRace(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody RaceRequest request) {
        return ApiResponse.<RaceResponse>builder()
                .success(true)
                .message("Race created")
                .data(raceService.createRace(userId, request))
                .build();
    }

    /** PUT /api/v1/races/{id} — partial update of a race. */
    @PutMapping("/{id}")
    public ApiResponse<RaceResponse> updateRace(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody RaceRequest request) {
        return ApiResponse.<RaceResponse>builder()
                .success(true)
                .message("Race updated")
                .data(raceService.updateRace(userId, id, request))
                .build();
    }

    /** DELETE /api/v1/races/{id} — soft-delete a race. */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteRace(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        raceService.deleteRace(userId, id);
        return ApiResponse.<Void>builder()
                .success(true)
                .message("Race deleted")
                .build();
    }

    /** PATCH /api/v1/races/{id}/schedule — set times and open the race. */
    @PatchMapping("/{id}/schedule")
    public ApiResponse<RaceResponse> scheduleRace(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody ScheduleRaceRequest request) {
        return ApiResponse.<RaceResponse>builder()
                .success(true)
                .message("Race scheduled")
                .data(raceService.scheduleRace(userId, id, request))
                .build();
    }

    /** PATCH /api/v1/races/{id}/cancel — cancel a race. */
    @PatchMapping("/{id}/cancel")
    public ApiResponse<RaceResponse> cancelRace(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        return ApiResponse.<RaceResponse>builder()
                .success(true)
                .message("Race cancelled")
                .data(raceService.cancelRace(userId, id))
                .build();
    }

    /** POST /api/v1/races/{id}/entries — assign an approved registration to the race. */
    @PostMapping("/{id}/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RaceEntryResponse> assignParticipant(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody AssignParticipantRequest request) {
        return ApiResponse.<RaceEntryResponse>builder()
                .success(true)
                .message("Participant assigned")
                .data(raceService.assignParticipant(userId, id, request))
                .build();
    }

    /** GET /api/v1/races/{id}/entries — list a race's participants. */
    @GetMapping("/{id}/entries")
    public ApiResponse<List<RaceEntryResponse>> listEntries(@PathVariable UUID id) {
        return ApiResponse.<List<RaceEntryResponse>>builder()
                .success(true)
                .message("Fetched race entries")
                .data(raceService.listEntries(id))
                .build();
    }

    /** GET /api/v1/races/{raceId}/my-entry — the caller-owner's own entry in this race. */
    @GetMapping("/{raceId}/my-entry")
    public ApiResponse<MyEntryResponse> getMyEntry(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID raceId) {
        return ApiResponse.<MyEntryResponse>builder()
                .success(true)
                .message("Fetched your race entry")
                .data(raceService.getMyEntry(raceId, userId))
                .build();
    }
}
