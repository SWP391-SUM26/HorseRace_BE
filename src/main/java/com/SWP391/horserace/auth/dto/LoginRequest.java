package com.SWP391.horserace.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Email + password login payload. */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password) {
}
