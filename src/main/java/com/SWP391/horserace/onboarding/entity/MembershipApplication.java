package com.SWP391.horserace.onboarding.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Maps the {@code membership_application} table (see db/schema_v4.sql).
 *
 * <p>Dedicated onboarding dossier: a person applies for a role; a {@code RACE_REFEREE}
 * reviews and approves/rejects/requests-info. Approval creates/activates an {@code app_user}.
 *
 * <p>PII note: {@code tax_id} and {@code id_document_ref} are stored plain in dev.
 * // TODO: encrypt at rest (prod). The API only exposes a masked tax id.
 */
@Entity
@Table(name = "membership_application")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MembershipApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "application_id", updatable = false, nullable = false)
    private UUID applicationId;

    @Column(name = "application_code", nullable = false, unique = true, length = 50)
    private String applicationCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_role", nullable = false, length = 30)
    private RequestedRole requestedRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 30)
    private ApplicationPriority priority;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    /** PII — stored plain in dev. // TODO: encrypt at rest (prod). Exposed only masked. */
    @Column(name = "tax_id", length = 100)
    private String taxId;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "avatar_url", columnDefinition = "text")
    private String avatarUrl;

    @Column(name = "location")
    private String location;

    @Column(name = "org_name")
    private String orgName;

    @Enumerated(EnumType.STRING)
    @Column(name = "id_verification_status", nullable = false, length = 30)
    @Builder.Default
    private IdVerificationStatus idVerificationStatus = IdVerificationStatus.PENDING;

    /** PII — stored plain in dev. // TODO: encrypt at rest (prod). */
    @Column(name = "id_document_ref")
    private String idDocumentRef;

    @Column(name = "license_class", length = 100)
    private String licenseClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "license_status", length = 30)
    private LicenseStatus licenseStatus;

    @Column(name = "license_valid_until")
    private LocalDate licenseValidUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "background_check_status", nullable = false, length = 30)
    @Builder.Default
    private BackgroundCheckStatus backgroundCheckStatus = BackgroundCheckStatus.PENDING;

    @Column(name = "submitted_at", nullable = false)
    @Builder.Default
    private OffsetDateTime submittedAt = OffsetDateTime.now();

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "requested_info_note", columnDefinition = "text")
    private String requestedInfoNote;

    /** Set when Approve & Onboard creates/activates the account. */
    @Column(name = "created_user_id")
    private UUID createdUserId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;
}
