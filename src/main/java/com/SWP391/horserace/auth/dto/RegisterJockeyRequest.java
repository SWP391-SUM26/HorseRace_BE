package com.SWP391.horserace.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for jockey self-registration.
 * Maps to the "Jockey Registration" form in the Figma design.
 *
 * <p>Section 1 — Personal Identity: firstName, lastName, age, weight, nationality.<br>
 * Section 2 — Experience: yearsActive, ridingStyle.<br>
 * Section 3 — Credentials: jockeyLicenseUrl, fitnessCertificateUrl (file upload TBD).</p>
 *
 * <p>Email and password are added here (not on the Figma form) so that the jockey can
 * log in after registration.</p>
 */
public record RegisterJockeyRequest(

        // ---- Account credentials (required by backend logic for login) ----

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password,

        @NotBlank(message = "Confirm password is required")
        String confirmPassword,

        // ---- Section 1: Personal Identity ----

        @NotBlank(message = "First name is required")
        String firstName,

        @NotBlank(message = "Last name is required")
        String lastName,

        /** Optional — age in years. */
        @Min(value = 16, message = "Jockey must be at least 16 years old")
        Integer age,

        /** Optional — weight in lbs. */
        Double weight,

        /** Optional — nationality / country code (e.g. "VN", "US"). */
        String nationality,

        // ---- Section 2: Experience ----

        /** Optional — number of professional years active. */
        Integer yearsActive,

        /**
         * Optional — primary riding style.
         * e.g. "FLAT", "JUMP", "HARNESS", "ENDURANCE"
         */
        String ridingStyle,

        // ---- Section 3: Credentials (file upload TBD — pass URL or leave null) ----

        /** Optional — URL of jockey license copy. */
        String jockeyLicenseUrl,

        /** Optional — URL of current fitness certificate. */
        String fitnessCertificateUrl
) {
}
