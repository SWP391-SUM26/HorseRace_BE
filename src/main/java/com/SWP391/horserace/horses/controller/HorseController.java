package com.SWP391.horserace.horses.controller;

import com.SWP391.horserace.horses.dto.HorseFilterRequest;
import com.SWP391.horserace.horses.dto.HorseRequest;
import com.SWP391.horserace.horses.dto.HorseResponse;
import com.SWP391.horserace.horses.service.HorseService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
}
