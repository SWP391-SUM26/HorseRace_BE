package com.SWP391.horserace.auth.service;

/**
 * Password-reset lifecycle: forgot (send code) and reset (verify code + change password).
 */
public interface PasswordResetService {

    /**
     * Generate a 6-digit reset code and send it to the registered email.
     * Silently succeeds even if the email is not found (to prevent user enumeration).
     */
    void forgotPassword(String email);

    /**
     * Resend a new 6-digit reset code to the same email.
     * Invalidates any previously issued (unused) code and enforces cooldown.
     */
    void resendCode(String email);

    /**
     * Verify the 6-digit code without changing the password.
     * Returns true if the code is valid and not expired.
     * Used by the frontend to validate the code before showing the new-password form.
     */
    void verifyCode(String email, String code);

    /**
     * Verify the 6-digit code, update the user's password, and revoke all refresh tokens.
     */
    void resetPassword(String email, String code, String newPassword, String confirmPassword);
}
