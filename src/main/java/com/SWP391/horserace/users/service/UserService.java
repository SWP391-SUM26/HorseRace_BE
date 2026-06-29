package com.SWP391.horserace.users.service;

import com.SWP391.horserace.users.dto.ChangeRoleRequest;
import com.SWP391.horserace.users.dto.ChangeStatusRequest;
import com.SWP391.horserace.users.dto.CreateUserRequest;
import com.SWP391.horserace.users.dto.UpdateProfileRequest;
import com.SWP391.horserace.users.dto.UserFilterRequest;
import com.SWP391.horserace.users.dto.UserResponse;
import com.SWP391.horserace.users.dto.UserStatsResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface UserService {
    UserResponse getUserById(UUID id);
    List<UserResponse> getAllUsers();

    /** First-place finishes of horses owned by this user (admin user-detail). */
    List<com.SWP391.horserace.users.dto.UserWinResponse> getUserWins(UUID userId);

    /**
     * Admin: filtered, paginated user listing (non-deleted users only). Supports free-text
     * {@code q} (name/email/code), {@code roleCode}, {@code status}, plus page/size/sort.
     */
    Page<UserResponse> listUsers(UserFilterRequest filter);

    /** Admin: aggregate user counts (total + by role + by status) over non-deleted users. */
    UserStatsResponse getUserStats();

    /** Admin: change a user's role by role code. */
    UserResponse changeRole(UUID id, ChangeRoleRequest request);

    /** Admin: change a user's status (ACTIVE / INACTIVE / SUSPENDED / BANNED). */
    UserResponse changeStatus(UUID id, ChangeStatusRequest request);

    /** Admin: provision a new ACTIVE user with the given role; rejects duplicate emails. */
    UserResponse createUser(CreateUserRequest request);

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

    /**
     * The permission codes granted to the given user (resolved from role -> role_permission ->
     * permission). Returns an empty list if the user has no role or the role has no permissions.
     */
    List<String> getMyPermissions(UUID userId);

    /** Admin: soft-delete a user by id (sets is_deleted/deleted_at). */
    void deleteUser(UUID id);
}
