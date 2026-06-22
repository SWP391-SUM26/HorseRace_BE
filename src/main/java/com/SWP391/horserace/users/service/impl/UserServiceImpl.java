package com.SWP391.horserace.users.service.impl;

import com.SWP391.horserace.roles.entity.Role;
import com.SWP391.horserace.roles.repository.PermissionRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.shared.storage.ImageUploadService;
import com.SWP391.horserace.users.dto.UpdateProfileRequest;
import com.SWP391.horserace.users.dto.UserResponse;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import com.SWP391.horserace.users.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final ImageUploadService imageUploadService;
    private final PermissionRepository permissionRepository;

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        return mapToResponse(loadActiveUser(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAllByDeletedFalse().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getMyProfile(UUID userId) {
        return mapToResponse(loadActiveUser(userId));
    }

    @Override
    @Transactional
    public UserResponse updateMyProfile(UUID userId, UpdateProfileRequest request) {
        User user = loadActiveUser(userId);

        // Partial update: only apply the fields the client actually sent (non-null).
        if (request.fullName() != null) {
            user.setFullName(request.fullName().trim());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone().trim());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl().trim());
        }

        // updated_at is maintained by Hibernate @UpdateTimestamp on flush.
        return mapToResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse updateUserById(UUID id, UpdateProfileRequest request) {
        User user = loadActiveUser(id);

        // Partial update of display fields only (same scope as self-service update).
        if (request.fullName() != null) {
            user.setFullName(request.fullName().trim());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone().trim());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl().trim());
        }

        return mapToResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse updateAvatar(UUID userId, MultipartFile file) {
        if (userId == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        User user = loadActiveUser(userId);

        String oldAvatarUrl = user.getAvatarUrl();
        user.setAvatarUrl(imageUploadService.storeImageAsUrl(file, "avatars"));
        UserResponse response = mapToResponse(userRepository.save(user));
        imageUploadService.deleteByUrl(oldAvatarUrl); // best-effort cleanup of the replaced file
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getMyPermissions(UUID userId) {
        User user = loadActiveUser(userId);
        Role role = user.getRole();
        if (role == null || role.getRoleId() == null) {
            return List.of();
        }
        return permissionRepository.findPermissionCodesByRoleId(role.getRoleId());
    }

    @Override
    @Transactional
    public void deleteUser(UUID id) {
        User user = loadActiveUser(id);
        user.setDeleted(true);
        user.setDeletedAt(OffsetDateTime.now());
        userRepository.save(user);
    }

    private User loadActiveUser(UUID userId) {
        return userRepository.findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
    }

    private UserResponse mapToResponse(User user) {
        Role role = user.getRole();
        return UserResponse.builder()
                .userId(user.getUserId())
                .userCode(user.getUserCode())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus() != null ? user.getStatus().name() : null)
                .kycStatus(user.getKycStatus() != null ? user.getKycStatus().name() : null)
                .roleCode(role != null ? role.getRoleCode() : null)
                .roleName(role != null ? role.getRoleName() : null)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
