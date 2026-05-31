package com.SWP391.horserace.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Carries the Google ID token obtained by the frontend's "Sign in with Google" flow. */
public record GoogleLoginRequest(
        @NotBlank String idToken) {
}
