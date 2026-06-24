package com.SWP391.horserace.onboarding.dto;

import com.SWP391.horserace.onboarding.entity.ApplicationStatus;
import com.SWP391.horserace.onboarding.entity.BackgroundCheckStatus;
import com.SWP391.horserace.onboarding.entity.IdVerificationStatus;
import com.SWP391.horserace.onboarding.entity.LicenseStatus;
import com.SWP391.horserace.onboarding.entity.RequestedRole;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Full dossier for the Registration Approval detail screen. */
@Data
@Builder
public class ApplicationDetail {
    private UUID applicationId;
    private String applicationCode;
    private String fullName;
    private RequestedRole requestedRole;
    private ApplicationStatus status;
    private String avatarUrl;
    private String location;
    /** Year derived from createdAt (when the dossier first appeared). */
    private String memberSince;
    private LocalDate dateOfBirth;
    /** PII — masked: XXX-XX-<last4>. */
    private String taxIdMasked;
    private String email;
    private String phone;
    private BusinessAffiliation businessAffiliation;
    private Eligibility eligibility;
    private OffsetDateTime submittedAt;
    private OffsetDateTime reviewedAt;
    private String rejectionReason;
    private String requestedInfoNote;

    @Data
    @Builder
    public static class BusinessAffiliation {
        private String orgName;
        private long horsesRegistered;
    }

    @Data
    @Builder
    public static class Eligibility {
        private IdVerification idVerification;
        private License license;
        private BackgroundCheck backgroundCheck;
    }

    @Data
    @Builder
    public static class IdVerification {
        private IdVerificationStatus status;
        private String documentRef;
    }

    @Data
    @Builder
    public static class License {
        /** Serialized as "class" per the FE contract; "clazz" avoids the Java keyword. */
        @JsonProperty("class")
        private String clazz;
        private LicenseStatus status;
        private LocalDate validUntil;
    }

    @Data
    @Builder
    public static class BackgroundCheck {
        private BackgroundCheckStatus status;
    }
}
