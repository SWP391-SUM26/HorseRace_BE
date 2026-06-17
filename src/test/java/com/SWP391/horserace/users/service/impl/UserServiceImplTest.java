package com.SWP391.horserace.users.service.impl;

import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.shared.storage.FileStorageService;
import com.SWP391.horserace.shared.storage.ImageUploadService;
import com.SWP391.horserace.users.dto.UpdateProfileRequest;
import com.SWP391.horserace.users.dto.UserResponse;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock UserRepository userRepository;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        // ImageUploadService is a concrete class (not mockable on this JVM); use a real instance
        // over a mocked storage — updateUserById never touches it.
        service = new UserServiceImpl(userRepository,
                new ImageUploadService(Mockito.mock(FileStorageService.class)));
    }

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
}
