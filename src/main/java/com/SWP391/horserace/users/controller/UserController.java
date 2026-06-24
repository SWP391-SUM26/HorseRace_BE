package com.SWP391.horserace.users.controller;

import com.SWP391.horserace.shared.dto.ApiResponse;
import com.SWP391.horserace.users.dto.ChangeEmailRequest;
import com.SWP391.horserace.users.dto.ChangeRoleRequest;
import com.SWP391.horserace.users.dto.ChangeStatusRequest;
import com.SWP391.horserace.users.dto.CreateUserRequest;
import com.SWP391.horserace.users.dto.UpdateProfileRequest;
import com.SWP391.horserace.users.dto.UserFilterRequest;
import com.SWP391.horserace.users.dto.UserResponse;
import com.SWP391.horserace.users.dto.UserStatsResponse;
import com.SWP391.horserace.users.dto.VerifyEmailChangeRequest;
import com.SWP391.horserace.users.service.EmailChangeService;
import com.SWP391.horserace.users.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final EmailChangeService emailChangeService;

    /** GET /api/v1/users/me — the current authenticated user's own profile. */
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMyProfile(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.<UserResponse>builder()
                .success(true)
                .message("Fetched current user profile")
                .data(userService.getMyProfile(userId))
                .build();
    }

    /** PUT /api/v1/users/me — update the current authenticated user's own profile. */
    @PutMapping("/me")
    public ApiResponse<UserResponse> updateMyProfile(@AuthenticationPrincipal UUID userId,
                                                     @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.<UserResponse>builder()
                .success(true)
                .message("Profile updated")
                .data(userService.updateMyProfile(userId, request))
                .build();
    }

    /**
     * POST /api/v1/users/me/avatar — upload an avatar image (multipart, field name "file").
     * Stores the file, sets the user's avatarUrl, and returns the updated profile.
     */
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UserResponse> uploadAvatar(@AuthenticationPrincipal UUID userId,
                                                  @RequestParam("file") MultipartFile file) {
        return ApiResponse.<UserResponse>builder()
                .success(true)
                .message("Avatar updated")
                .data(userService.updateAvatar(userId, file))
                .build();
    }

    /**
     * POST /api/v1/users/me/email/change-request — step 1 of a verified email change.
     * Validates the new address and sends a one-time code to it. Email is NOT changed yet.
     */
    @PostMapping("/me/email/change-request")
    public ApiResponse<Void> requestEmailChange(@AuthenticationPrincipal UUID userId,
                                                @Valid @RequestBody ChangeEmailRequest request) {
        emailChangeService.requestEmailChange(userId, request.newEmail());
        return ApiResponse.<Void>builder()
                .success(true)
                .message("Verification code sent to the new email address")
                .build();
    }

    /**
     * POST /api/v1/users/me/email/verify — step 2 of a verified email change.
     * Confirms the one-time code and applies the new email to the account.
     */
    @PostMapping("/me/email/verify")
    public ApiResponse<UserResponse> verifyEmailChange(@AuthenticationPrincipal UUID userId,
                                                       @Valid @RequestBody VerifyEmailChangeRequest request) {
        return ApiResponse.<UserResponse>builder()
                .success(true)
                .message("Email updated")
                .data(emailChangeService.confirmEmailChange(userId, request.code()))
                .build();
    }

    /**
     * GET /api/v1/users/me/permissions — the current authenticated user's permission codes
     * (resolved from role -> role_permission -> permission). Lets the FE drive UI from real
     * permissions instead of hardcoding them.
     */
    @GetMapping("/me/permissions")
    public ApiResponse<List<String>> getMyPermissions(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.<List<String>>builder()
                .success(true)
                .message("Fetched current user permissions")
                .data(userService.getMyPermissions(userId))
                .build();
    }

    /** GET /api/v1/users/{id}/permissions — permission codes for a given user (admin/lookup). */
    @GetMapping("/{id}/permissions")
    public ApiResponse<List<String>> getUserPermissions(@PathVariable UUID id) {
        return ApiResponse.<List<String>>builder()
                .success(true)
                .message("Fetched user permissions")
                .data(userService.getMyPermissions(id))
                .build();
    }

    /**
     * GET /api/v1/users — admin: filtered, paginated user listing. Query params bound via
     * {@link UserFilterRequest}: {@code q}, {@code roleCode}, {@code status}, {@code page},
     * {@code size}, {@code sortBy}, {@code sortDir}. Returns a Spring {@code Page<UserResponse>}.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Page<UserResponse>> listUsers(@ModelAttribute UserFilterRequest filter) {
        return ApiResponse.<Page<UserResponse>>builder()
                .success(true)
                .message("Fetched users")
                .data(userService.listUsers(filter))
                .build();
    }

    /**
     * GET /api/v1/users/stats — admin: aggregate user counts
     * ({@code totalUsers}, {@code byRole}, {@code byStatus}) over non-deleted users.
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserStatsResponse> getUserStats() {
        return ApiResponse.<UserStatsResponse>builder()
                .success(true)
                .message("Fetched user stats")
                .data(userService.getUserStats())
                .build();
    }

    /**
     * POST /api/v1/users — admin: provision a new ACTIVE user with the given role.
     * Body {@link CreateUserRequest}. Returns 201 with the created user.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.<UserResponse>builder()
                .success(true)
                .message("User created")
                .data(userService.createUser(request))
                .build();
    }

    /** GET /api/v1/users/{id} — one user by UUID. */
    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getUserById(@PathVariable UUID id) {
        return ApiResponse.<UserResponse>builder()
                .success(true)
                .message("Fetched user")
                .data(userService.getUserById(id))
                .build();
    }

    /** PATCH /api/v1/users/{id}/role — admin: change a user's role by role code. */
    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> changeRole(@PathVariable UUID id,
                                                @Valid @RequestBody ChangeRoleRequest request) {
        return ApiResponse.<UserResponse>builder()
                .success(true)
                .message("User role updated")
                .data(userService.changeRole(id, request))
                .build();
    }

    /** PATCH /api/v1/users/{id}/status — admin: suspend / activate / ban a user. */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> changeStatus(@PathVariable UUID id,
                                                  @Valid @RequestBody ChangeStatusRequest request) {
        return ApiResponse.<UserResponse>builder()
                .success(true)
                .message("User status updated")
                .data(userService.changeStatus(id, request))
                .build();
    }

    /**
     * PUT /api/v1/users/{id} — admin updates a user's display profile by id
     * (fullName / phone / avatarUrl). Admin-intended; role enforcement is deferred to the
     * RBAC phase (consistent with the rest of the API's current dev posture).
     */
    @PutMapping("/{id}")
    public ApiResponse<UserResponse> updateUserById(@PathVariable UUID id,
                                                    @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.<UserResponse>builder()
                .success(true)
                .message("User updated")
                .data(userService.updateUserById(id, request))
                .build();
    }

    /** DELETE /api/v1/users/{id} — admin soft-delete a user by UUID. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ApiResponse.<Void>builder()
                .success(true)
                .message("User deleted")
                .build();
    }
}
