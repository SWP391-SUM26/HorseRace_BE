package com.SWP391.horserace.owner.service;

import com.SWP391.horserace.attachments.dto.AttachmentResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Owner-facing upload of RESTRICTED owner/horse documents (FR-07, FR-08). Enforces ownership of the
 * target horse (RT-CRITICAL-5) and triggers the FR-12 reset-to-PENDING of any rejected reviews.
 */
public interface OwnerDocumentService {

    /**
     * Upload one owner/horse document as the authenticated owner.
     *
     * @param callerId  the authenticated owner
     * @param file      the multipart file
     * @param ownerType {@code OWNER} (owner papers) or {@code HORSE} (horse papers)
     * @param horseId   required for {@code HORSE}; must belong to the caller. Ignored for {@code OWNER}.
     */
    AttachmentResponse uploadDocument(UUID callerId, MultipartFile file, String ownerType, UUID horseId);

    /** The caller's own OWNER documents, newest first. */
    java.util.List<AttachmentResponse> listMyOwnerDocuments(UUID callerId);

    /** A horse's documents — the horse must belong to the caller (ownership-checked). */
    java.util.List<AttachmentResponse> listHorseDocuments(UUID callerId, UUID horseId);
}
