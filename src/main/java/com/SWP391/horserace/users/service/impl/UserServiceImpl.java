package com.SWP391.horserace.users.service.impl;

import com.SWP391.horserace.roles.entity.Role;
import com.SWP391.horserace.roles.repository.PermissionRepository;
import com.SWP391.horserace.roles.repository.RoleRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
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
import com.SWP391.horserace.users.repository.UserRepository;
import com.SWP391.horserace.users.repository.UserSpecification;
import com.SWP391.horserace.users.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    /** Allow-list of entity fields a client may sort by, mapped from the public sortBy values. */
    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "createdat", "createdAt",
            "fullname", "fullName",
            "name", "fullName",
            "email", "email",
            "lastloginat", "lastLoginAt",
            "status", "status");

    /** Default temporary password applied when the admin omits {@code tempPassword}. */
    private static final String DEFAULT_TEMP_PASSWORD = "123456";

    private final UserRepository userRepository;
    private final ImageUploadService imageUploadService;
    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;

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
    public Page<UserResponse> listUsers(UserFilterRequest filter) {
        UserFilterRequest f = filter != null ? filter : UserFilterRequest.builder().build();
        Pageable pageable = PageRequest.of(f.resolvedPage(), f.resolvedSize(), resolveSort(f));
        return userRepository.findAll(UserSpecification.withFilters(f), pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UserStatsResponse getUserStats() {
        long total = userRepository.countByDeletedFalse();

        Map<String, Long> byRole = new LinkedHashMap<>();
        for (Object[] row : userRepository.countByRoleCodeGrouped()) {
            byRole.put((String) row[0], (Long) row[1]);
        }

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Object[] row : userRepository.countByStatusGrouped()) {
            UserStatus status = (UserStatus) row[0];
            byStatus.put(status != null ? status.name() : null, (Long) row[1]);
        }

        return UserStatsResponse.builder()
                .totalUsers(total)
                .byRole(byRole)
                .byStatus(byStatus)
                .build();
    }

    @Override
    @Transactional
    public UserResponse changeRole(UUID id, ChangeRoleRequest request) {
        User user = loadActiveUser(id);
        Role role = roleRepository.findByRoleCode(request.roleCode().trim().toUpperCase())
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_EXISTED));
        user.setRole(role);
        return mapToResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse changeStatus(UUID id, ChangeStatusRequest request) {
        User user = loadActiveUser(id);
        UserStatus status = parseStatus(request.status());
        user.setStatus(status);
        if (request.reason() != null && !request.reason().isBlank()) {
            // No dedicated audit column today — log the reason for traceability.
            log.info("User {} status changed to {} (reason: {})", id, status, request.reason().trim());
        }
        return mapToResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        Role role = roleRepository.findByRoleCode(request.roleCode().trim().toUpperCase())
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_EXISTED));

        String tempPassword = (request.tempPassword() != null && !request.tempPassword().isBlank())
                ? request.tempPassword()
                : DEFAULT_TEMP_PASSWORD;

        User user = User.builder()
                .role(role)
                .userCode(generateUserCode())
                .fullName(request.fullName().trim())
                .email(normalizedEmail)
                .phone(request.phone() != null ? request.phone().trim() : null)
                // Stored {noop}-prefixed so the DelegatingPasswordEncoder can verify it; rotate later.
                .passwordHash("{noop}" + tempPassword)
                .status(UserStatus.ACTIVE)
                .build();

        return mapToResponse(userRepository.save(user));
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

    /** Translate the public {@code sortBy}/{@code sortDir} into a JPA {@link Sort} over an allow-listed field. */
    private Sort resolveSort(UserFilterRequest filter) {
        String key = filter.getSortBy() != null ? filter.getSortBy().trim().toLowerCase() : "";
        String field = SORTABLE_FIELDS.getOrDefault(key, "createdAt");
        Sort.Direction direction = "asc".equalsIgnoreCase(filter.getSortDir())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }

    /** Parse a status string to {@link UserStatus}; throws {@link ErrorCode#INVALID_USER_STATUS} if invalid. */
    private UserStatus parseStatus(String raw) {
        try {
            return UserStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new AppException(ErrorCode.INVALID_USER_STATUS);
        }
    }

    /** Generates a short, human-readable user code: {@code USR-XXXXXXXX} (matches the auth module). */
    private String generateUserCode() {
        return "USR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
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
                .emailVerified(user.isEmailVerified())
                .googleId(user.getGoogleId())
                .roleCode(role != null ? role.getRoleCode() : null)
                .roleName(role != null ? role.getRoleName() : null)
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
