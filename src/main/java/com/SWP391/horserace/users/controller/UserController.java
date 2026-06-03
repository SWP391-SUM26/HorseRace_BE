package com.SWP391.horserace.users.controller;

import com.SWP391.horserace.shared.dto.ApiResponse;
import com.SWP391.horserace.users.dto.ChangeEmailRequest;
import com.SWP391.horserace.users.dto.UpdateProfileRequest;
import com.SWP391.horserace.users.dto.UserResponse;
import com.SWP391.horserace.users.dto.VerifyEmailChangeRequest;
import com.SWP391.horserace.users.service.EmailChangeService;
import com.SWP391.horserace.users.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    /** GET /api/v1/users — list all active users. */
    @GetMapping
    public ApiResponse<List<UserResponse>> getAllUsers() {
        return ApiResponse.<List<UserResponse>>builder()
                .success(true)
                .message("Fetched all users")
                .data(userService.getAllUsers())
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
}
