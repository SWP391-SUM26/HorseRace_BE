package com.SWP391.horserace.users.service.impl;

import com.SWP391.horserace.roles.entity.Role;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.shared.storage.FileStorageService;
import com.SWP391.horserace.users.dto.UpdateProfileRequest;
import com.SWP391.horserace.users.dto.UserResponse;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.repository.UserRepository;
import com.SWP391.horserace.users.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Set<String> ALLOWED_AVATAR_TYPES =
            Set.of("image/png", "image/jpeg", "image/jpg", "image/webp", "image/gif");
    private static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024; // 5MB

    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

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
    public UserResponse updateAvatar(UUID userId, MultipartFile file) {
        User user = loadActiveUser(userId);

        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.FILE_EMPTY);
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_AVATAR_TYPES.contains(contentType.toLowerCase())) {
            throw new AppException(ErrorCode.INVALID_FILE_TYPE);
        }

        String key = fileStorageService.store(file, "avatars");
        user.setAvatarUrl("/api/v1/files/" + key);
        return mapToResponse(userRepository.save(user));
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
