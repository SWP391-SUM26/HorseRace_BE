package com.SWP391.horserace.attachments.controller;

import com.SWP391.horserace.attachments.dto.AttachmentResponse;
import com.SWP391.horserace.attachments.service.AttachmentService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    /** POST /api/v1/attachments — multipart upload of a file attached to a polymorphic owner (FE-v2 §6). */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AttachmentResponse> upload(
            @AuthenticationPrincipal UUID userId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("ownerEntityType") String ownerEntityType,
            @RequestParam("ownerEntityId") UUID ownerEntityId,
            @RequestParam(value = "sensitivityLevel", required = false) String sensitivityLevel) {
        return ApiResponse.<AttachmentResponse>builder()
                .success(true)
                .message("Attachment uploaded")
                .data(attachmentService.upload(userId, file, ownerEntityType, ownerEntityId, sensitivityLevel))
                .build();
    }

    /** GET /api/v1/attachments?ownerEntityType=&ownerEntityId= — list attachments for an owner (FE-v2 §6). */
    @GetMapping
    public ApiResponse<List<AttachmentResponse>> list(
            @RequestParam("ownerEntityType") String ownerEntityType,
            @RequestParam("ownerEntityId") UUID ownerEntityId) {
        return ApiResponse.<List<AttachmentResponse>>builder()
                .success(true)
                .message("Fetched attachments")
                .data(attachmentService.listByOwner(ownerEntityType, ownerEntityId))
                .build();
    }
}
