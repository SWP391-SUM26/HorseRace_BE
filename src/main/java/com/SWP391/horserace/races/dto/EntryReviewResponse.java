package com.SWP391.horserace.races.dto;

import com.SWP391.horserace.attachments.dto.AttachmentResponse;
import com.SWP391.horserace.inspections.entity.DocumentReviewStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Per-entry document-review row for the referee's per-race review screen (CN2 / FR-10). */
@Data
@Builder
public class EntryReviewResponse {
    private UUID entryId;
    private Integer entryNo;
    private String horseName;
    private String ownerName;
    private DocumentReviewStatus documentStatus;
    private String reviewReason;
    private String reviewedByName;
    private OffsetDateTime reviewedAt;
    private List<AttachmentResponse> ownerDocs;
    private List<AttachmentResponse> horseDocs;
}
