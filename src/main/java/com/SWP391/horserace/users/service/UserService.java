package com.SWP391.horserace.users.service;

import com.SWP391.horserace.users.dto.UpdateProfileRequest;
import com.SWP391.horserace.users.dto.UserResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface UserService {
    UserResponse getUserById(UUID id);
    List<UserResponse> getAllUsers();

    /** The current authenticated user's own profile. */
    UserResponse getMyProfile(UUID userId);

    /** Apply a partial profile update for the given user and persist it. */
    UserResponse updateMyProfile(UUID userId, UpdateProfileRequest request);

    /**
     * Admin: update any user's display profile by id (fullName / phone / avatarUrl). Identity and
     * security fields (email, password, role, status, kyc) are intentionally out of scope here.
     */
    UserResponse updateUserById(UUID id, UpdateProfileRequest request);

    /** Store an uploaded avatar image, set the user's avatarUrl, and return the updated profile. */
    UserResponse updateAvatar(UUID userId, MultipartFile file);
}
