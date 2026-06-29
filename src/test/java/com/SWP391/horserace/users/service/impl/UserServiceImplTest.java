package com.SWP391.horserace.users.service.impl;

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

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        // ImageUploadService is a concrete class (not mockable on this JVM); construct
        // a real
        // instance over a mocked FileStorageService — not exercised by these tests.
        service = new UserServiceImpl(userRepository,
                new ImageUploadService(Mockito.mock(FileStorageService.class)),
                permissionRepository,
                roleRepository);
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
    void createUser_provisionsActiveUserWithRoleAndNoopPassword() {
        Role role = Role.builder().roleCode("JOCKEY").roleName("Jockey").build();
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(roleRepository.findByRoleCode("JOCKEY")).thenReturn(Optional.of(role));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse res = service.createUser(
                new CreateUserRequest("New User", "New@Example.com", "jockey", null, null));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();

        assertThat(saved.getEmail()).isEqualTo("new@example.com");
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getPasswordHash()).isEqualTo("{noop}123456");
        assertThat(saved.getUserCode()).startsWith("USR-");
        assertThat(saved.getRole()).isSameAs(role);
        assertThat(res.getRoleCode()).isEqualTo("JOCKEY");
    }

    @Test
    void createUser_usesProvidedTempPassword() {
        Role role = Role.builder().roleCode("VET").roleName("Vet").build();
        when(userRepository.existsByEmail("vet@example.com")).thenReturn(false);
        when(roleRepository.findByRoleCode("VET")).thenReturn(Optional.of(role));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        service.createUser(new CreateUserRequest("Doc", "vet@example.com", "VET", null, "Secret99"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("{noop}Secret99");
    }

    @Test
    void createUser_duplicateEmail_throwsEmailAlreadyExists() {
        when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.createUser(
                new CreateUserRequest("Dup", "dup@example.com", "ADMIN", null, null)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_ALREADY_EXISTS);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createUser_badRoleCode_throwsRoleNotExisted() {
        when(userRepository.existsByEmail("x@example.com")).thenReturn(false);
        when(roleRepository.findByRoleCode("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createUser(
                new CreateUserRequest("X", "x@example.com", "NOPE", null, null)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROLE_NOT_EXISTED);
        verify(userRepository, never()).save(any(User.class));
    }
}
