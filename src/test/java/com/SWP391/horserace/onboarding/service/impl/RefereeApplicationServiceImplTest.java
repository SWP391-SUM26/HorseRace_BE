package com.SWP391.horserace.onboarding.service.impl;

import com.SWP391.horserace.horses.repository.HorseRepository;
import com.SWP391.horserace.onboarding.dto.ApplicationDetail;
import com.SWP391.horserace.onboarding.dto.ApplicationSummary;
import com.SWP391.horserace.onboarding.dto.OnboardingStatsResponse;
import com.SWP391.horserace.onboarding.entity.ApplicationStatus;
import com.SWP391.horserace.onboarding.entity.BackgroundCheckStatus;
import com.SWP391.horserace.onboarding.entity.IdVerificationStatus;
import com.SWP391.horserace.onboarding.entity.MembershipApplication;
import com.SWP391.horserace.onboarding.entity.RequestedRole;
import com.SWP391.horserace.onboarding.repository.MembershipApplicationRepository;
import com.SWP391.horserace.roles.entity.Role;
import com.SWP391.horserace.roles.repository.RoleRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.KycStatus;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.entity.UserStatus;
import com.SWP391.horserace.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefereeApplicationServiceImplTest {

    @Mock MembershipApplicationRepository applicationRepository;
    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock HorseRepository horseRepository;

    private RefereeApplicationServiceImpl service;

    private final UUID reviewerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RefereeApplicationServiceImpl(
                applicationRepository, userRepository, roleRepository, horseRepository);
    }

    private MembershipApplication pendingOwner() {
        return MembershipApplication.builder()
                .applicationId(UUID.randomUUID())
                .applicationCode("APP-0001")
                .requestedRole(RequestedRole.OWNER)
                .status(ApplicationStatus.PENDING)
                .fullName("Jane Applicant")
                .email("jane.applicant@example.com")
                .taxId("123-45-6789")
                .idVerificationStatus(IdVerificationStatus.VALID)
                .backgroundCheckStatus(BackgroundCheckStatus.PASSED)
                .submittedAt(OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Test
    void approve_createsUser_andSetsApproved() {
        MembershipApplication app = pendingOwner();
        UUID newUserId = UUID.randomUUID();
        when(applicationRepository.findById(app.getApplicationId())).thenReturn(Optional.of(app));
        // approve() looks up only non-deleted accounts; empty here -> create path.
        when(userRepository.findByEmailAndDeletedFalse(app.getEmail())).thenReturn(Optional.empty());
        // toDetail() horses lookup resolves the freshly created user.
        when(userRepository.findByEmail(app.getEmail()))
                .thenReturn(Optional.of(User.builder().userId(newUserId).build()));
        Role ownerRole = Role.builder().roleId(UUID.randomUUID()).roleCode("HORSE_OWNER").build();
        when(roleRepository.findByRoleCode("HORSE_OWNER")).thenReturn(Optional.of(ownerRole));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setUserId(newUserId);
            return u;
        });
        when(applicationRepository.save(any(MembershipApplication.class))).thenAnswer(inv -> inv.getArgument(0));
        when(horseRepository.countByOwner_UserIdAndDeletedFalse(newUserId)).thenReturn(3L);

        ApplicationDetail detail = service.approve(app.getApplicationId(), reviewerId);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getKycStatus()).isEqualTo(KycStatus.VERIFIED);
        assertThat(saved.getRole().getRoleCode()).isEqualTo("HORSE_OWNER");
        assertThat(saved.getPasswordHash()).isEqualTo("{noop}123456");

        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(app.getCreatedUserId()).isEqualTo(newUserId);
        assertThat(app.getReviewedByUserId()).isEqualTo(reviewerId);
        assertThat(app.getReviewedAt()).isNotNull();
        assertThat(detail.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(detail.getBusinessAffiliation().getHorsesRegistered()).isEqualTo(3L);
        assertThat(detail.getTaxIdMasked()).isEqualTo("XXX-XX-6789");
    }

    @Test
    void approve_existingUser_activatesInsteadOfCreating() {
        MembershipApplication app = pendingOwner();
        UUID existingId = UUID.randomUUID();
        User existing = User.builder()
                .userId(existingId)
                .email(app.getEmail())
                .status(UserStatus.INACTIVE)
                .kycStatus(KycStatus.PENDING)
                .build();
        when(applicationRepository.findById(app.getApplicationId())).thenReturn(Optional.of(app));
        // approve() activates the existing non-deleted account; toDetail() reuses findByEmail.
        when(userRepository.findByEmailAndDeletedFalse(app.getEmail())).thenReturn(Optional.of(existing));
        when(userRepository.findByEmail(app.getEmail())).thenReturn(Optional.of(existing));
        Role ownerRole = Role.builder().roleId(UUID.randomUUID()).roleCode("HORSE_OWNER").build();
        when(roleRepository.findByRoleCode("HORSE_OWNER")).thenReturn(Optional.of(ownerRole));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(applicationRepository.save(any(MembershipApplication.class))).thenAnswer(inv -> inv.getArgument(0));
        when(horseRepository.countByOwner_UserIdAndDeletedFalse(existingId)).thenReturn(0L);

        service.approve(app.getApplicationId(), reviewerId);

        assertThat(existing.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(existing.getKycStatus()).isEqualTo(KycStatus.VERIFIED);
        // The requested role must be honored on the existing-account path, not left stale.
        assertThat(existing.getRole().getRoleCode()).isEqualTo("HORSE_OWNER");
        assertThat(app.getCreatedUserId()).isEqualTo(existingId);
    }

    @Test
    void approve_alreadyDecided_throws() {
        MembershipApplication app = pendingOwner();
        app.setStatus(ApplicationStatus.APPROVED);
        when(applicationRepository.findById(app.getApplicationId())).thenReturn(Optional.of(app));

        assertThatThrownBy(() -> service.approve(app.getApplicationId(), reviewerId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_ALREADY_DECIDED);

        verify(userRepository, never()).save(any());
    }

    @Test
    void reject_setsRejectedWithReason() {
        MembershipApplication app = pendingOwner();
        when(applicationRepository.findById(app.getApplicationId())).thenReturn(Optional.of(app));
        when(applicationRepository.save(any(MembershipApplication.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByEmail(app.getEmail())).thenReturn(Optional.empty());

        ApplicationDetail detail = service.reject(app.getApplicationId(), "Bad docs", reviewerId);

        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(app.getRejectionReason()).isEqualTo("Bad docs");
        assertThat(app.getReviewedByUserId()).isEqualTo(reviewerId);
        assertThat(detail.getRejectionReason()).isEqualTo("Bad docs");
    }

    @Test
    void reject_alreadyDecided_throws() {
        MembershipApplication app = pendingOwner();
        app.setStatus(ApplicationStatus.REJECTED);
        when(applicationRepository.findById(app.getApplicationId())).thenReturn(Optional.of(app));

        assertThatThrownBy(() -> service.reject(app.getApplicationId(), "x", reviewerId))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ErrorCode.APPLICATION_ALREADY_DECIDED);
    }

    @Test
    void requestInfo_setsInfoRequestedWithNote() {
        MembershipApplication app = pendingOwner();
        when(applicationRepository.findById(app.getApplicationId())).thenReturn(Optional.of(app));
        when(applicationRepository.save(any(MembershipApplication.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByEmail(app.getEmail())).thenReturn(Optional.empty());

        service.requestInfo(app.getApplicationId(), "Send ID copy", reviewerId);

        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.INFO_REQUESTED);
        assertThat(app.getRequestedInfoNote()).isEqualTo("Send ID copy");
        assertThat(app.getReviewedByUserId()).isEqualTo(reviewerId);
    }

    @Test
    void stats_scopesApprovedAndRejectedToToday() {
        when(applicationRepository.countByStatus(ApplicationStatus.PENDING)).thenReturn(5L);
        when(applicationRepository.countByStatusAndReviewedAtGreaterThanEqual(
                eq(ApplicationStatus.APPROVED), any(OffsetDateTime.class))).thenReturn(2L);
        when(applicationRepository.countByStatusAndReviewedAtGreaterThanEqual(
                eq(ApplicationStatus.REJECTED), any(OffsetDateTime.class))).thenReturn(1L);

        OnboardingStatsResponse stats = service.stats();

        assertThat(stats.getPendingApprovals()).isEqualTo(5L);
        assertThat(stats.getApprovedToday()).isEqualTo(2L);
        assertThat(stats.getRejectedToday()).isEqualTo(1L);
    }

    @Test
    void list_appliesFilterAndMapsSummaries() {
        MembershipApplication app = pendingOwner();
        Pageable pageable = PageRequest.of(0, 10);
        Page<MembershipApplication> page = new PageImpl<>(List.of(app), pageable, 1);
        when(applicationRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        Page<ApplicationSummary> result = service.list(
                ApplicationStatus.PENDING, RequestedRole.OWNER, "jane", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getApplicationCode()).isEqualTo("APP-0001");
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(ApplicationStatus.PENDING);
    }
}
