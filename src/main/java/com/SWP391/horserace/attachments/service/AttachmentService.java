package com.SWP391.horserace.attachments.service;

import com.SWP391.horserace.attachments.dto.AttachmentResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** Upload + list of polymorphic file attachments (FE-v2 §6). */
public interface AttachmentService {

    /** File bytes + metadata for a secured (non-public) download. */
    record AttachmentDownload(Resource resource, String fileName, String mimeType) {}

    /**
     * Load an attachment's bytes for a secured download. Caller authorization is enforced at the
     * controller — used for sensitive files that must NOT go through the public /files route.
     */
    AttachmentDownload download(UUID attachmentId);

    /**
     * Store the file and insert an {@code attachment} row.
     *
     * @param userId          authenticated uploader id ({@code null} → UNAUTHENTICATED)
     * @param file            the multipart file
     * @param ownerEntityType RACE_RESULT | VIOLATION | RACE
     * @param ownerEntityId   id of the owning entity (no FK by design)
     * @param sensitivityLevel optional — PUBLIC | INTERNAL | CONFIDENTIAL | RESTRICTED
     */
    AttachmentResponse upload(UUID userId, MultipartFile file, String ownerEntityType,
                              UUID ownerEntityId, String sensitivityLevel);

    /**
     * Upload an owner/horse document (owner-types {@code OWNER} / {@code HORSE}). These are ALWAYS
     * stored RESTRICTED (referee/admin download only). Ownership of the {@code ownerEntityId} must be
     * enforced by the caller (the /owner/documents endpoint). The generic {@link #upload} path rejects
     * these owner-types so every owner/horse doc is forced through the ownership-checked endpoint.
     *
     * @param ownerEntityType OWNER | HORSE
     * @param ownerEntityId   the owner's userId (OWNER) or the horse's horseId (HORSE)
     */
    AttachmentResponse uploadOwnerDocument(UUID userId, MultipartFile file, String ownerEntityType,
                                           UUID ownerEntityId);

    /** All attachments for a polymorphic owner, newest first. */
    List<AttachmentResponse> listByOwner(String ownerEntityType, UUID ownerEntityId);

    /**
     * List owner/horse documents ({@code OWNER}/{@code HORSE} owner-types) — the generic
     * {@link #listByOwner} rejects those types. Ownership must be enforced by the caller
     * (the /owner/documents endpoint). Newest first.
     */
    List<AttachmentResponse> listOwnerDocuments(String ownerEntityType, UUID ownerEntityId);
}
