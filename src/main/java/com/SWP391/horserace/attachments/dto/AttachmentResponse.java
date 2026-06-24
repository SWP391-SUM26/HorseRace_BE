package com.SWP391.horserace.attachments.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Response for attachment upload + list (FE-v2 §6). */
@Data
@Builder
public class AttachmentResponse {
    private UUID attachmentId;
    private String ownerEntityType;
    private UUID ownerEntityId;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    /** Public URL through the file controller: {@code /api/v1/files/{key}}. */
    private String url;
    /** PUBLIC | INTERNAL | CONFIDENTIAL | RESTRICTED (nullable). */
    private String sensitivityLevel;
    private OffsetDateTime uploadedAt;
}
