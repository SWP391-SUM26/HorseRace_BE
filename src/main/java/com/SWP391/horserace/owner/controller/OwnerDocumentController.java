package com.SWP391.horserace.owner.controller;

import com.SWP391.horserace.attachments.dto.AttachmentResponse;
import com.SWP391.horserace.attachments.service.AttachmentService;
import com.SWP391.horserace.owner.service.OwnerDocumentService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Owner-facing RESTRICTED document upload (FR-07, FR-08). Ownership-checked (RT-CRITICAL-5) — the
 * generic /api/v1/attachments path rejects OWNER/HORSE owner-types so uploads must come through here.
 */
@RestController
@RequestMapping("/api/v1/owner")
@RequiredArgsConstructor
public class OwnerDocumentController {

    private final OwnerDocumentService ownerDocumentService;

    /**
     * POST /api/v1/owner/documents — upload an owner ({@code OWNER}) or horse ({@code HORSE}) document.
     * For {@code HORSE}, {@code horseId} is required and must belong to the caller.
     */
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('HORSE_OWNER')")
    public ApiResponse<AttachmentResponse> uploadDocument(
            @AuthenticationPrincipal UUID callerId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("ownerType") String ownerType,
            @RequestParam(value = "horseId", required = false) UUID horseId) {
        return ApiResponse.<AttachmentResponse>builder()
                .success(true)
                .message("Document uploaded")
                .data(ownerDocumentService.uploadDocument(callerId, file, ownerType, horseId))
                .build();
    }

    /** GET /api/v1/owner/documents — the caller's own OWNER documents. */
    @org.springframework.web.bind.annotation.GetMapping("/documents")
    @PreAuthorize("hasRole('HORSE_OWNER')")
    public ApiResponse<java.util.List<AttachmentResponse>> myDocuments(
            @AuthenticationPrincipal UUID callerId) {
        return ApiResponse.<java.util.List<AttachmentResponse>>builder()
                .success(true)
                .message("Fetched owner documents")
                .data(ownerDocumentService.listMyOwnerDocuments(callerId))
                .build();
    }

    /** GET /api/v1/owner/documents/horse/{horseId} — a horse's documents (must belong to the caller). */
    @org.springframework.web.bind.annotation.GetMapping("/documents/horse/{horseId}")
    @PreAuthorize("hasRole('HORSE_OWNER')")
    public ApiResponse<java.util.List<AttachmentResponse>> horseDocuments(
            @AuthenticationPrincipal UUID callerId,
            @org.springframework.web.bind.annotation.PathVariable UUID horseId) {
        return ApiResponse.<java.util.List<AttachmentResponse>>builder()
                .success(true)
                .message("Fetched horse documents")
                .data(ownerDocumentService.listHorseDocuments(callerId, horseId))
                .build();
    }

    /** GET /api/v1/owner/documents/{attachmentId}/download — stream one of the caller's OWNER/HORSE docs inline. */
    @GetMapping("/documents/{attachmentId}/download")
    @PreAuthorize("hasRole('HORSE_OWNER')")
    public ResponseEntity<Resource> downloadDocument(
            @AuthenticationPrincipal UUID callerId,
            @PathVariable UUID attachmentId) {
        AttachmentService.AttachmentDownload d = ownerDocumentService.downloadOwnerDocument(callerId, attachmentId);
        MediaType mt = d.mimeType() != null ? MediaType.parseMediaType(d.mimeType()) : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(mt)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + (d.fileName() != null ? d.fileName() : "document") + "\"")
                .body(d.resource());
    }
}
