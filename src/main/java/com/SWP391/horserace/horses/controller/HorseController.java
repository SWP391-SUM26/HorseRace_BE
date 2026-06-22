package com.SWP391.horserace.horses.controller;

import com.SWP391.horserace.horses.dto.AssignHorseToRaceRequest;
import com.SWP391.horserace.horses.dto.HorseFilterRequest;
import com.SWP391.horserace.horses.dto.HorseRequest;
import com.SWP391.horserace.horses.dto.HorseResponse;
import com.SWP391.horserace.horses.dto.MedicalStatusResponse;
import com.SWP391.horserace.horses.dto.RaceHistoryItemResponse;
import com.SWP391.horserace.horses.dto.UpdateMedicalStatusRequest;
import com.SWP391.horserace.horses.service.HorseService;
import com.SWP391.horserace.races.dto.RaceEntryResponse;
import com.SWP391.horserace.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/horses")
@RequiredArgsConstructor
public class HorseController {

    private final HorseService horseService;

    /** GET /api/v1/horses — list with search (q), filters, sort and pagination. */
    @GetMapping
    public ApiResponse<Page<HorseResponse>> listHorses(@ModelAttribute HorseFilterRequest filter) {
        return ApiResponse.<Page<HorseResponse>>builder()
                .success(true)
                .message("Fetched horses")
                .data(horseService.listHorses(filter))
                .build();
    }

    /** GET /api/v1/horses/{id} — one horse. */
    @GetMapping("/{id}")
    public ApiResponse<HorseResponse> getHorse(@PathVariable UUID id) {
        return ApiResponse.<HorseResponse>builder()
                .success(true)
                .message("Fetched horse")
                .data(horseService.getHorseById(id))
                .build();
    }

    /** POST /api/v1/horses — create a horse owned by the current user. */
    @PostMapping
    public ApiResponse<HorseResponse> createHorse(@AuthenticationPrincipal UUID userId,
                                                  @Valid @RequestBody HorseRequest request) {
        return ApiResponse.<HorseResponse>builder()
                .success(true)
                .message("Horse created")
                .data(horseService.createHorse(userId, request))
                .build();
    }

    /** PUT /api/v1/horses/{id} — partial update (owner only). */
    @PutMapping("/{id}")
    public ApiResponse<HorseResponse> updateHorse(@AuthenticationPrincipal UUID userId,
                                                  @PathVariable UUID id,
                                                  @Valid @RequestBody HorseRequest request) {
        return ApiResponse.<HorseResponse>builder()
                .success(true)
                .message("Horse updated")
                .data(horseService.updateHorse(userId, id, request))
                .build();
    }

    /** DELETE /api/v1/horses/{id} — soft-delete (owner only). */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteHorse(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        horseService.deleteHorse(userId, id);
        return ApiResponse.<Void>builder()
                .success(true)
                .message("Horse deleted")
                .build();
    }

    /** POST /api/v1/horses/{id}/image — upload/replace the horse photo (owner only). */
    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<HorseResponse> uploadHorseImage(@AuthenticationPrincipal UUID userId,
                                                       @PathVariable UUID id,
                                                       @RequestParam("file") MultipartFile file) {
        return ApiResponse.<HorseResponse>builder()
                .success(true)
                .message("Horse image updated")
                .data(horseService.updateHorseImage(userId, id, file))
                .build();
    }

    /** GET /api/v1/horses/{id}/medical-status — current medical status. */
    @GetMapping("/{id}/medical-status")
    public ApiResponse<MedicalStatusResponse> getMedicalStatus(@PathVariable UUID id) {
        return ApiResponse.<MedicalStatusResponse>builder()
                .success(true)
                .message("Fetched medical status")
                .data(horseService.getMedicalStatus(id))
                .build();
    }

    /** PATCH /api/v1/horses/{id}/medical-status — partial update (owner or admin). */
    @PatchMapping("/{id}/medical-status")
    public ApiResponse<MedicalStatusResponse> updateMedicalStatus(@AuthenticationPrincipal UUID userId,
                                                                  @PathVariable UUID id,
                                                                  @Valid @RequestBody UpdateMedicalStatusRequest request) {
        return ApiResponse.<MedicalStatusResponse>builder()
                .success(true)
                .message("Medical status updated")
                .data(horseService.updateMedicalStatus(userId, id, request))
                .build();
    }

    /** GET /api/v1/horses/{id}/race-history — all races the horse has been entered into. */
    @GetMapping("/{id}/race-history")
    public ApiResponse<List<RaceHistoryItemResponse>> getRaceHistory(@PathVariable UUID id) {
        return ApiResponse.<List<RaceHistoryItemResponse>>builder()
                .success(true)
                .message("Fetched race history")
                .data(horseService.getRaceHistory(id))
                .build();
    }

    /** POST /api/v1/horses/{id}/assign-to-race — enter the horse into a race (owner or admin). */
    @PostMapping("/{id}/assign-to-race")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RaceEntryResponse> assignToRace(@AuthenticationPrincipal UUID userId,
                                                       @PathVariable UUID id,
                                                       @Valid @RequestBody AssignHorseToRaceRequest request) {
        return ApiResponse.<RaceEntryResponse>builder()
                .success(true)
                .message("Horse assigned to race")
                .data(horseService.assignHorseToRace(userId, id, request))
                .build();
    }
}
