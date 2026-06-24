package com.SWP391.horserace.attachments.service;

import com.SWP391.horserace.attachments.dto.AttachmentResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** Upload + list of polymorphic file attachments (FE-v2 §6). */
public interface AttachmentService {

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

    /** All attachments for a polymorphic owner, newest first. */
    List<AttachmentResponse> listByOwner(String ownerEntityType, UUID ownerEntityId);
}
