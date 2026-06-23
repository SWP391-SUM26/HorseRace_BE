package com.SWP391.horserace.jockeys.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One of an owner's race entries that does not yet have an accepted jockey.
 * Powers the left-hand column of the Jockey Market (FE-v2 §2):
 * {@code GET /api/v1/owner/unassigned-entries}.
 */
@Data
@Builder
public class UnassignedEntryResponse {
    private UUID registrationId;
    private UUID horseId;
    private String horseName;
    private UUID raceId;
    private String raceName;
    private OffsetDateTime raceDate;
}
