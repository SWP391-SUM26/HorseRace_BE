package com.SWP391.horserace.staffing.service;

import java.util.UUID;

/**
 * Emailed one-time submission code (CN3): a referee requests a 6-digit OTP to their verified email
 * before publishing a race report, then validates it once on submit.
 */
public interface RefereeSubmissionCodeService {

    /**
     * Issue a fresh code to the CONFIRMED referee's verified email (SHA-256 hash + TTL stored).
     * Throttled: rejects a request that follows the previous one too closely.
     */
    void requestCode(UUID refereeUserId, UUID raceId);

    /**
     * Validate the referee's provided code for the race and consume it (single-use). Rejects
     * missing/expired/used codes, enforces the attempt limit, and increments attempts on a mismatch.
     */
    void validateAndConsume(UUID refereeUserId, UUID raceId, String providedCode);
}
