package com.SWP391.horserace.users.service;

import com.SWP391.horserace.users.dto.UpdateProfileRequest;
import com.SWP391.horserace.users.dto.UserResponse;

import java.util.List;
import java.util.UUID;

public interface UserService {
    UserResponse getUserById(UUID id);
    List<UserResponse> getAllUsers();

    /** The current authenticated user's own profile. */
    UserResponse getMyProfile(UUID userId);

    /** Apply a partial profile update for the given user and persist it. */
    UserResponse updateMyProfile(UUID userId, UpdateProfileRequest request);
}
