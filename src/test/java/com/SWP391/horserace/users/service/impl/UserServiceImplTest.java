package com.SWP391.horserace.users.service.impl;

import com.SWP391.horserace.races.repository.RaceResultRepository;
import com.SWP391.horserace.roles.entity.Role;
import com.SWP391.horserace.roles.repository.PermissionRepository;
import com.SWP391.horserace.roles.repository.RoleRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.shared.storage.FileStorageService;
import com.SWP391.horserace.shared.storage.ImageUploadService;
import com.SWP391.horserace.users.dto.ChangeRoleRequest;
import com.SWP391.horserace.users.dto.ChangeStatusRequest;
import com.SWP391.horserace.users.dto.CreateUserRequest;
import com.SWP391.horserace.users.dto.UpdateProfileRequest;
import com.SWP391.horserace.users.dto.UserFilterRequest;
import com.SWP391.horserace.users.dto.UserResponse;
import com.SWP391.horserace.users.dto.UserStatsResponse;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.SWP391.horserace.auth.service.EmailService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.SWP391.horserace.users.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    UserRepository userRepository;
    @Mock
    PermissionRepository permissionRepository;
    @Mock
    RoleRepository roleRepository;
    @Mock
    RaceResultRepository raceResultRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    EmailService emailService;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        // ImageUploadService is a concrete class (not mockable on this JVM); construct
        // a real
        // instance over a mocked FileStorageService — not exercised by these tests.
        service = new UserServiceImpl(userRepository,
                new ImageUploadService(Mockito.mock(FileStorageService.class)),
                permissionRepository,
                roleRepository,
                raceResultRepository,
                passwordEncoder,
                emailService);
    }

    // ── update user by id (admin) ──

    @Test
    void updateUserById_appliesFullNameAndPhone() {
        UUID id = UUID.randomUUID();
        User user = User.builder().userId(id).fullName("Old Name").phone("0900000000").build();
        when(userRepository.findByUserIdAndDeletedFalse(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse res = service.updateUserById(id,
                new UpdateProfileRequest("New Name", "0911222333", null));

        assertThat(res.getFullName()).isEqualTo("New Name");
        assertThat(res.getPhone()).isEqualTo("0911222333");
    }

    @Test
    void updateUserById_userNotFound_throws() {
        UUID id = UUID.randomUUID();
        when(userRepository.findByUserIdAndDeletedFalse(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateUserById(id,
                new UpdateProfileRequest("X", null, null)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_EXISTED);
    }

    // ── permissions ──

    @Test
    void getMyPermissions_returnsRolePermissionCodes() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        User user = User.builder()
                .userId(userId)
                .role(Role.builder().roleId(roleId).roleCode("ADMIN").build())
                .build();
        List<String> codes = List.of("USER_MANAGE", "ROLE_MANAGE", "PROFILE_MANAGE");

        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(permissionRepository.findPermissionCodesByRoleId(roleId)).thenReturn(codes);

        List<String> result = service.getMyPermissions(userId);

        assertThat(result).containsExactlyElementsOf(codes);
        verify(permissionRepository).findPermissionCodesByRoleId(roleId);
    }

    @Test
    void getMyPermissions_userWithoutRole_returnsEmptyList() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().userId(userId).role(null).build();
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(user));

        List<String> result = service.getMyPermissions(userId);

        assertThat(result).isEmpty();
        verify(permissionRepository, never()).findPermissionCodesByRoleId(Mockito.any());
    }

    @Test
    void getMyPermissions_userNotFound_throwsUserNotExisted() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyPermissions(userId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_EXISTED);
    }

    @Test
    void deleteUser_softDeletesUser() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().userId(userId).build();
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(Mockito.any(User.class))).thenAnswer(i -> i.getArgument(0));

        service.deleteUser(userId);

        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getDeletedAt()).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    void deleteUser_userNotFound_throwsUserNotExisted() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findByUserIdAndDeletedFalse(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteUser(userId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_EXISTED);
    }

    // ── list / filter (B3) — maps new fields (status, lastLoginAt) ──

    @Test
    @SuppressWarnings("unchecked")
    void listUsers_mapsResponsesIncludingNewFields() {
        OffsetDateTime loginAt = OffsetDateTime.now().minusDays(1);
        User user = User.builder()
                .userId(UUID.randomUUID())
                .userCode("USR-ABC")
                .fullName("Jane Doe")
                .email("jane@example.com")
                .status(UserStatus.SUSPENDED)
                .lastLoginAt(loginAt)
                .role(Role.builder().roleCode("ADMIN").roleName("Administrator").build())
                .build();
        Page<User> page = new PageImpl<>(List.of(user));
        when(userRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<UserResponse> result = service.listUsers(UserFilterRequest.builder().q("jane").build());

        assertThat(result.getContent()).hasSize(1);
        UserResponse res = result.getContent().get(0);
        assertThat(res.getStatus()).isEqualTo("SUSPENDED");
        assertThat(res.getLastLoginAt()).isEqualTo(loginAt);
        assertThat(res.getRoleCode()).isEqualTo("ADMIN");
        assertThat(res.getEmail()).isEqualTo("jane@example.com");
    }

    @Test
    @SuppressWarnings("unchecked")
    void listUsers_nullFilter_usesDefaultsAndCapsPageSize() {
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<UserResponse> result = service.listUsers(null);

        assertThat(result).isNotNull();
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(any(Specification.class), pageableCaptor.capture());
        // default size 20 by default builder
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listUsers_oversizedPageSize_isCappedAt100() {
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listUsers(UserFilterRequest.builder().size(5000).build());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }

    // ── stats (B4) ──

    @Test
    void getUserStats_aggregatesTotalsByRoleAndStatus() {
        when(userRepository.countByDeletedFalse()).thenReturn(10L);
        when(userRepository.countByRoleCodeGrouped()).thenReturn(List.of(
                new Object[] { "ADMIN", 1L },
                new Object[] { "JOCKEY", 4L }));
        when(userRepository.countByStatusGrouped()).thenReturn(List.of(
                new Object[] { UserStatus.ACTIVE, 9L },
                new Object[] { UserStatus.SUSPENDED, 1L }));

        UserStatsResponse stats = service.getUserStats();

        assertThat(stats.getTotalUsers()).isEqualTo(10L);
        assertThat(stats.getByRole()).containsEntry("ADMIN", 1L).containsEntry("JOCKEY", 4L);
        assertThat(stats.getByStatus()).containsEntry("ACTIVE", 9L).containsEntry("SUSPENDED", 1L);
    }

    // ── changeRole (B5) ──

    @Test
    void changeRole_setsRoleByCode() {
        UUID id = UUID.randomUUID();
        User user = User.builder().userId(id)
                .role(Role.builder().roleCode("SPECTATOR").roleName("Spectator").build())
                .build();
        Role newRole = Role.builder().roleCode("JOCKEY").roleName("Jockey").build();
        when(userRepository.findByUserIdAndDeletedFalse(id)).thenReturn(Optional.of(user));
        when(roleRepository.findByRoleCode("JOCKEY")).thenReturn(Optional.of(newRole));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse res = service.changeRole(id, new ChangeRoleRequest("jockey"));

        assertThat(res.getRoleCode()).isEqualTo("JOCKEY");
        assertThat(user.getRole()).isSameAs(newRole);
    }

    @Test
    void changeRole_badRoleCode_throwsRoleNotExisted() {
        UUID id = UUID.randomUUID();
        User user = User.builder().userId(id).build();
        when(userRepository.findByUserIdAndDeletedFalse(id)).thenReturn(Optional.of(user));
        when(roleRepository.findByRoleCode("BOGUS")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeRole(id, new ChangeRoleRequest("BOGUS")))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROLE_NOT_EXISTED);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changeRole_userNotFound_throwsUserNotExisted() {
        UUID id = UUID.randomUUID();
        when(userRepository.findByUserIdAndDeletedFalse(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeRole(id, new ChangeRoleRequest("ADMIN")))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_EXISTED);
    }

    // ── changeStatus (B6) ──

    @Test
    void changeStatus_setsStatus() {
        UUID id = UUID.randomUUID();
        User user = User.builder().userId(id).status(UserStatus.ACTIVE).build();
        when(userRepository.findByUserIdAndDeletedFalse(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse res = service.changeStatus(id, new ChangeStatusRequest("suspended", "spam"));

        assertThat(res.getStatus()).isEqualTo("SUSPENDED");
        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    void changeStatus_invalidStatus_throwsInvalidUserStatus() {
        UUID id = UUID.randomUUID();
        User user = User.builder().userId(id).status(UserStatus.ACTIVE).build();
        when(userRepository.findByUserIdAndDeletedFalse(id)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.changeStatus(id, new ChangeStatusRequest("ON_FIRE", null)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_USER_STATUS);
        verify(userRepository, never()).save(any(User.class));
    }

    // ── createUser / provision (B7) ──

    @Test
    void createUser_bcryptsGeneratedPassword_andEmailsIt() {
        Role role = Role.builder().roleCode("JOCKEY").roleName("Jockey").build();
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(roleRepository.findByRoleCode("JOCKEY")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode(any())).thenReturn("{bcrypt}$2a$HASH");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse res = service.createUser(
                new CreateUserRequest("New User", "New@Example.com", "jockey", null));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();

        assertThat(saved.getEmail()).isEqualTo("new@example.com");
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        // Admin-provisioned accounts are email-verified (password emailed to that address) so staff
        // like referees can be assigned immediately without a separate verify step.
        assertThat(saved.isEmailVerified()).isTrue();
        assertThat(saved.getPasswordHash()).isEqualTo("{bcrypt}$2a$HASH"); // bcrypt, NOT {noop}
        assertThat(saved.getPasswordHash()).doesNotStartWith("{noop}");
        assertThat(saved.getRole()).isSameAs(role);
        assertThat(res.getRoleCode()).isEqualTo("JOCKEY");
        // the generated (raw) password is emailed to the new user
        verify(emailService).sendNewAccountPassword(org.mockito.ArgumentMatchers.eq("new@example.com"),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void createUser_generatedPassword_meetsComplexityRules() {
        Role role = Role.builder().roleCode("SPECTATOR").build();
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(roleRepository.findByRoleCode("SPECTATOR")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode(any())).thenReturn("{bcrypt}x");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        service.createUser(new CreateUserRequest("N", "n@example.com", "SPECTATOR", null));

        ArgumentCaptor<String> pwCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendNewAccountPassword(any(), pwCaptor.capture());
        String raw = pwCaptor.getValue();
        assertThat(raw.length()).isGreaterThanOrEqualTo(8);
        assertThat(raw).matches(".*[A-Z].*").matches(".*[a-z].*")
                .matches(".*\\d.*").matches(".*[^A-Za-z0-9].*");
    }

    @Test
    void createUser_adminRole_isBlocked() {
        when(userRepository.existsByEmail("boss@example.com")).thenReturn(false);

        assertThatThrownBy(() -> service.createUser(
                new CreateUserRequest("Boss", "boss@example.com", "admin", null)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANNOT_CREATE_ADMIN);
        verify(userRepository, never()).save(any(User.class));
        verify(emailService, never()).sendNewAccountPassword(any(), any());
    }

    @Test
    void createUser_duplicateEmail_throwsEmailAlreadyExists() {
        when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.createUser(
                new CreateUserRequest("Dup", "dup@example.com", "JOCKEY", null)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_ALREADY_EXISTS);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createUser_duplicatePhone_throwsPhoneAlreadyExists() {
        Role role = Role.builder().roleCode("JOCKEY").build();
        when(userRepository.existsByEmail("p@example.com")).thenReturn(false);
        when(roleRepository.findByRoleCode("JOCKEY")).thenReturn(Optional.of(role));
        when(userRepository.existsByPhone("0912345678")).thenReturn(true);

        assertThatThrownBy(() -> service.createUser(
                new CreateUserRequest("P", "p@example.com", "JOCKEY", "0912345678")))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PHONE_ALREADY_EXISTS);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createUser_normalizesPhoneBeforeUniquenessCheck() {
        // FR-11: a dashed/spaced phone is stripped to its digits before the uniqueness check,
        // so formatting cannot bypass the dedupe against a stored "0912345678".
        Role role = Role.builder().roleCode("JOCKEY").build();
        when(userRepository.existsByEmail("p@example.com")).thenReturn(false);
        when(roleRepository.findByRoleCode("JOCKEY")).thenReturn(Optional.of(role));
        when(userRepository.existsByPhone("0912345678")).thenReturn(true);

        assertThatThrownBy(() -> service.createUser(
                new CreateUserRequest("P", "p@example.com", "JOCKEY", "09 12-345-678")))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PHONE_ALREADY_EXISTS);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createUser_nullPhone_skipsCheck() {
        Role role = Role.builder().roleCode("JOCKEY").build();
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(roleRepository.findByRoleCode("JOCKEY")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode(any())).thenReturn("{bcrypt}x");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        service.createUser(new CreateUserRequest("N", "n@example.com", "JOCKEY", null));

        verify(userRepository, never()).existsByPhone(any());
    }

    @Test
    void updateUserById_phoneTakenByAnother_throws() {
        UUID id = UUID.randomUUID();
        User user = User.builder().userId(id).build();
        when(userRepository.findByUserIdAndDeletedFalse(id)).thenReturn(Optional.of(user));
        when(userRepository.existsByPhoneAndUserIdNot("0912345678", id)).thenReturn(true);

        assertThatThrownBy(() -> service.updateUserById(id,
                new UpdateProfileRequest(null, "0912345678", null)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PHONE_ALREADY_EXISTS);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateMyProfile_keepingOwnPhone_succeeds() {
        UUID id = UUID.randomUUID();
        User user = User.builder().userId(id).phone("0912345678").build();
        when(userRepository.findByUserIdAndDeletedFalse(id)).thenReturn(Optional.of(user));
        // The user's own phone is not "taken by another" -> guard passes.
        when(userRepository.existsByPhoneAndUserIdNot("0912345678", id)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse res = service.updateMyProfile(id,
                new UpdateProfileRequest(null, "0912345678", null));

        assertThat(res.getPhone()).isEqualTo("0912345678");
    }

    @Test
    void createUser_badRoleCode_throwsRoleNotExisted() {
        when(userRepository.existsByEmail("x@example.com")).thenReturn(false);
        when(roleRepository.findByRoleCode("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createUser(
                new CreateUserRequest("X", "x@example.com", "NOPE", null)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROLE_NOT_EXISTED);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void resendPassword_regeneratesBcryptAndEmails() {
        UUID id = UUID.randomUUID();
        User user = User.builder().userId(id).email("u@example.com").passwordHash("{bcrypt}old").build();
        when(userRepository.findByUserIdAndDeletedFalse(id)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(any())).thenReturn("{bcrypt}new");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        service.resendPassword(id);

        assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}new");
        verify(emailService).sendNewAccountPassword(org.mockito.ArgumentMatchers.eq("u@example.com"),
                org.mockito.ArgumentMatchers.anyString());
    }
}
