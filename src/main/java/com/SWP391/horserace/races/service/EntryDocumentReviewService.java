package com.SWP391.horserace.races.service;

import com.SWP391.horserace.races.dto.EntryReviewResponse;

import java.util.List;
import java.util.UUID;

/**
 * Referee's per-race document review of each entry's owner + horse papers (CN2 / FR-09..FR-12).
 * All read/write operations require the caller to be ADMIN or a CONFIRMED referee for the race.
 */
public interface EntryDocumentReviewService {

    /** Every entry of the race with its owner docs, horse docs, and current document status. */
    List<EntryReviewResponse> getEntryReviews(UUID callerId, UUID raceId);

    /** Mark an entry's documents ACCEPTED. */
    EntryReviewResponse acceptEntry(UUID callerId, UUID raceId, UUID entryId);

    /** Mark an entry's documents REJECTED with a mandatory reason. */
    EntryReviewResponse rejectEntry(UUID callerId, UUID raceId, UUID entryId, String reason);

    /**
     * FR-12 — when an owner re-uploads owner/horse papers, flip any REJECTED reviews for that owner's
     * (or that horse's) not-yet-final entries back to PENDING so the referee re-reviews them.
     *
     * @param ownerUserId the uploading owner (OWNER upload) — resets that owner's entries when horseId is null
     * @param horseId     the horse whose papers were uploaded (HORSE upload) — resets that horse's entries; may be null
     */
    void resetToPendingOnUpload(UUID ownerUserId, UUID horseId);
}
