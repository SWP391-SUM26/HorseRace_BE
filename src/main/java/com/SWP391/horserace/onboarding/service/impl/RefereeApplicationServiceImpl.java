package com.SWP391.horserace.onboarding.service.impl;

import com.SWP391.horserace.horses.repository.HorseRepository;
import com.SWP391.horserace.onboarding.dto.ApplicationDetail;
import com.SWP391.horserace.onboarding.dto.ApplicationSummary;
import com.SWP391.horserace.onboarding.dto.OnboardingStatsResponse;
import com.SWP391.horserace.onboarding.entity.ApplicationStatus;
import com.SWP391.horserace.onboarding.entity.MembershipApplication;
import com.SWP391.horserace.onboarding.entity.RequestedRole;
import com.SWP391.horserace.onboarding.repository.MembershipApplicationRepository;
import com.SWP391.horserace.onboarding.repository.MembershipApplicationSpecifications;
import com.SWP391.horserace.onboarding.service.RefereeApplicationService;
import com.SWP391.horserace.roles.entity.Role;
import com.SWP391.horserace.roles.repository.RoleRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.KycStatus;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.entity.UserStatus;
import com.SWP391.horserace.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefereeApplicationServiceImpl implements RefereeApplicationService {

    /** Statuses from which a referee may still take a decision. */
    private static final Set<ApplicationStatus> DECIDABLE =
            EnumSet.of(ApplicationStatus.PENDING, ApplicationStatus.UNDER_REVIEW, ApplicationStatus.INFO_REQUESTED);

    private final MembershipApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final HorseRepository horseRepository;

    @Override
    public Page<ApplicationSummary> list(ApplicationStatus status, RequestedRole requestedRole,
                                         String q, Pageable pageable) {
        return applicationRepository
                .findAll(MembershipApplicationSpecifications.filter(status, requestedRole, q), pageable)
                .map(this::toSummary);
    }

    @Override
    public OnboardingStatsResponse stats() {
        OffsetDateTime startOfToday = startOfServerDay();
        return OnboardingStatsResponse.builder()
                .pendingApprovals(applicationRepository.countByStatus(ApplicationStatus.PENDING))
                .approvedToday(applicationRepository
                        .countByStatusAndReviewedAtGreaterThanEqual(ApplicationStatus.APPROVED, startOfToday))
                .rejectedToday(applicationRepository
                        .countByStatusAndReviewedAtGreaterThanEqual(ApplicationStatus.REJECTED, startOfToday))
                .build();
    }

    @Override
    public ApplicationDetail getDetail(UUID applicationId) {
        return toDetail(findOrThrow(applicationId));
    }

    @Override
    @Transactional
    public ApplicationDetail approve(UUID applicationId, UUID reviewerUserId) {
        MembershipApplication app = findOrThrow(applicationId);
        ensureDecidable(app);

        // Resolve the target role from the requested role for both create and activate paths.
        Role role = roleRepository.findByRoleCode(mapRoleCode(app.getRequestedRole()))
                .orElseThrow(() -> new AppException(ErrorCode.APPLICATION_ROLE_NOT_FOUND));

        // Activate an existing (non-deleted) account by email, or create a new one for the requested role.
        // Soft-deleted accounts are intentionally excluded so an approval never resurrects a removed user.
        User user = userRepository.findByEmailAndDeletedFalse(app.getEmail()).orElse(null);
        if (user == null) {
            user = User.builder()
                    .role(role)
                    .userCode(generateUserCode())
                    .fullName(app.getFullName())
                    .email(app.getEmail())
                    .phone(app.getPhone())
                    .passwordHash("{noop}123456")
                    .avatarUrl(app.getAvatarUrl())
                    .status(UserStatus.ACTIVE)
                    .kycStatus(KycStatus.VERIFIED)
                    .build();
            user = userRepository.save(user);
        } else {
            user.setRole(role);
            user.setStatus(UserStatus.ACTIVE);
            user.setKycStatus(KycStatus.VERIFIED);
            user = userRepository.save(user);
        }

        app.setStatus(ApplicationStatus.APPROVED);
        app.setCreatedUserId(user.getUserId());
        app.setReviewedAt(OffsetDateTime.now());
        app.setReviewedByUserId(reviewerUserId);
        app.setRejectionReason(null);
        return toDetail(applicationRepository.save(app));
    }

    @Override
    @Transactional
    public ApplicationDetail reject(UUID applicationId, String reason, UUID reviewerUserId) {
        MembershipApplication app = findOrThrow(applicationId);
        ensureDecidable(app);
        app.setStatus(ApplicationStatus.REJECTED);
        app.setRejectionReason(reason);
        app.setReviewedAt(OffsetDateTime.now());
        app.setReviewedByUserId(reviewerUserId);
        return toDetail(applicationRepository.save(app));
    }

    @Override
    @Transactional
    public ApplicationDetail requestInfo(UUID applicationId, String note, UUID reviewerUserId) {
        MembershipApplication app = findOrThrow(applicationId);
        ensureDecidable(app);
        app.setStatus(ApplicationStatus.INFO_REQUESTED);
        app.setRequestedInfoNote(note);
        app.setReviewedAt(OffsetDateTime.now());
        app.setReviewedByUserId(reviewerUserId);
        return toDetail(applicationRepository.save(app));
    }

    @Override
    public List<ApplicationSummary> history(UUID applicationId) {
        MembershipApplication app = findOrThrow(applicationId);
        return applicationRepository.findByEmailOrderBySubmittedAtDesc(app.getEmail()).stream()
                .filter(a -> !a.getApplicationId().equals(applicationId))
                .map(this::toSummary)
                .toList();
    }

    // ---- helpers ----

    private MembershipApplication findOrThrow(UUID applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new AppException(ErrorCode.APPLICATION_NOT_FOUND));
    }

    private void ensureDecidable(MembershipApplication app) {
        if (!DECIDABLE.contains(app.getStatus())) {
            throw new AppException(ErrorCode.APPLICATION_ALREADY_DECIDED);
        }
    }

    /** Maps the requested role to an app role_code. */
    private String mapRoleCode(RequestedRole requestedRole) {
        return switch (requestedRole) {
            case OWNER -> "HORSE_OWNER";
            case JOCKEY -> "JOCKEY";
            case TRAINER -> "TRAINER";
            case VET -> "VET";
        };
    }

    /** Collision-resistant code; seeded users use USR####, so we use an APP-prefixed UUID slice. */
    private String generateUserCode() {
        return "APPU" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private OffsetDateTime startOfServerDay() {
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime startOfDay = ZonedDateTime.now(zone).toLocalDate().atStartOfDay(zone);
        return startOfDay.toOffsetDateTime();
    }

    private String maskTaxId(String taxId) {
        if (taxId == null) {
            return null;
        }
        String digits = taxId.replaceAll("[^0-9]", "");
        String last4 = digits.length() >= 4 ? digits.substring(digits.length() - 4)
                : (taxId.length() >= 4 ? taxId.substring(taxId.length() - 4) : taxId);
        return "XXX-XX-" + last4;
    }

    private ApplicationSummary toSummary(MembershipApplication a) {
        return ApplicationSummary.builder()
                .applicationId(a.getApplicationId())
                .applicationCode(a.getApplicationCode())
                .fullName(a.getFullName())
                .requestedRole(a.getRequestedRole())
                .priority(a.getPriority())
                .status(a.getStatus())
                .submittedAt(a.getSubmittedAt())
                .build();
    }

    private ApplicationDetail toDetail(MembershipApplication a) {
        long horsesRegistered = 0L;
        if (a.getRequestedRole() == RequestedRole.OWNER) {
            horsesRegistered = userRepository.findByEmail(a.getEmail())
                    .map(u -> horseRepository.countByOwner_UserIdAndDeletedFalse(u.getUserId()))
                    .orElse(0L);
        }

        ApplicationDetail.Eligibility eligibility = ApplicationDetail.Eligibility.builder()
                .idVerification(ApplicationDetail.IdVerification.builder()
                        .status(a.getIdVerificationStatus())
                        .documentRef(a.getIdDocumentRef())
                        .build())
                .license(ApplicationDetail.License.builder()
                        .clazz(a.getLicenseClass())
                        .status(a.getLicenseStatus())
                        .validUntil(a.getLicenseValidUntil())
                        .build())
                .backgroundCheck(ApplicationDetail.BackgroundCheck.builder()
                        .status(a.getBackgroundCheckStatus())
                        .build())
                .build();

        return ApplicationDetail.builder()
                .applicationId(a.getApplicationId())
                .applicationCode(a.getApplicationCode())
                .fullName(a.getFullName())
                .requestedRole(a.getRequestedRole())
                .status(a.getStatus())
                .avatarUrl(a.getAvatarUrl())
                .location(a.getLocation())
                .memberSince(a.getCreatedAt() == null ? null : String.valueOf(a.getCreatedAt().getYear()))
                .dateOfBirth(a.getDateOfBirth())
                .taxIdMasked(maskTaxId(a.getTaxId()))
                .email(a.getEmail())
                .phone(a.getPhone())
                .businessAffiliation(ApplicationDetail.BusinessAffiliation.builder()
                        .orgName(a.getOrgName())
                        .horsesRegistered(horsesRegistered)
                        .build())
                .eligibility(eligibility)
                .submittedAt(a.getSubmittedAt())
                .reviewedAt(a.getReviewedAt())
                .rejectionReason(a.getRejectionReason())
                .requestedInfoNote(a.getRequestedInfoNote())
                .build();
    }
}
