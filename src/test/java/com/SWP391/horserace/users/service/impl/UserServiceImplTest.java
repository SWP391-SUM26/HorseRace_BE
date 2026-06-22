package com.SWP391.horserace.users.service.impl;

import com.SWP391.horserace.roles.entity.Role;
import com.SWP391.horserace.roles.repository.PermissionRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.shared.storage.FileStorageService;
import com.SWP391.horserace.shared.storage.ImageUploadService;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock PermissionRepository permissionRepository;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        // ImageUploadService is a concrete class (not mockable on this JVM); construct a real
        // instance over a mocked FileStorageService — it is not exercised by getMyPermissions.
        service = new UserServiceImpl(userRepository,
                new ImageUploadService(Mockito.mock(FileStorageService.class)),
                permissionRepository);
    }

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
}
