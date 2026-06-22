package com.SWP391.horserace.auth.service;

/**
 * Standalone email-verification lifecycle: request a 6-digit code, then verify it
 * to flag the account's email as verified. Independent of registration and login.
 */
public interface EmailVerificationService {

    /**
     * Generate a 6-digit verification code and email it to the account.
     * Silently succeeds even if the email is not found (to prevent user enumeration).
     * Email sending is best-effort: an SMTP failure does not fail the request.
     */
    void requestVerification(String email);

    /**
     * Verify the 6-digit code; on success flag the user's email as verified and
     * mark the token used. Invalid/expired codes throw a clear {@code AppException}.
     */
    void verifyEmail(String email, String code);
}
