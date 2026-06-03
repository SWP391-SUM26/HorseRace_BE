package com.SWP391.horserace.users.service;

import com.SWP391.horserace.users.dto.UserResponse;

import java.util.UUID;

/**
 * Two-step, verified email change for the current user. Step 1 issues a one-time code to the
 * proposed address; step 2 confirms it and applies the change.
 */
public interface EmailChangeService {

    /** Step 1 — validate the new email and issue a verification code to it. */
    void requestEmailChange(UUID userId, String newEmail);

    /** Step 2 — confirm the code and apply the new email to the account. */
    UserResponse confirmEmailChange(UUID userId, String code);
}
