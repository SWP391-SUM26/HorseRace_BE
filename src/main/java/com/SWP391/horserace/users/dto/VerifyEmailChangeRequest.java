package com.SWP391.horserace.users.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Step 2 of the verified email change — the user submits the one-time code that was sent to
 * the new address. On success the new email becomes effective on their account.
 */
public record VerifyEmailChangeRequest(

        @NotBlank(message = "Verification code is required")
        String code
) {
}
