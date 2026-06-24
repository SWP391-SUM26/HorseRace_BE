package com.SWP391.horserace.inspections.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** Result of PATCH /api/v1/races/{raceId}/inspections/submit-all. */
@Data
@Builder
public class SubmitAllResponse {
    private UUID raceId;
    private long submittedCount;
    private List<BlockedEntry> blockedEntries;

    /** An entry that could not be submitted because its inspection is not CLEARED. */
    @Data
    @Builder
    public static class BlockedEntry {
        private UUID entryId;
        private String horseName;
        private String reason;
    }
}
