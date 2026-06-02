package com.SWP391.horserace.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Step 1 of the verified email change — the user proposes a new email. A one-time code is
 * sent to that address; the change is NOT applied until the code is confirmed.
 */
public record ChangeEmailRequest(

        @NotBlank(message = "New email is required")
        @Email(message = "Email is invalid")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String newEmail
) {
}
