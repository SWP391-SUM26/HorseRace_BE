package com.SWP391.horserace.auth.controller;

import com.SWP391.horserace.auth.dto.AuthResponse;
import com.SWP391.horserace.auth.dto.ForgotPasswordRequest;
import com.SWP391.horserace.auth.dto.GoogleLoginRequest;
import com.SWP391.horserace.auth.dto.LoginRequest;
import com.SWP391.horserace.auth.dto.LogoutRequest;
import com.SWP391.horserace.auth.dto.RefreshRequest;
import com.SWP391.horserace.auth.dto.RegisterJockeyRequest;
import com.SWP391.horserace.auth.dto.RegisterOwnerRequest;
import com.SWP391.horserace.auth.dto.RegisterSpectatorRequest;
import com.SWP391.horserace.auth.dto.ResendCodeRequest;
import com.SWP391.horserace.auth.dto.ResetPasswordRequest;
import com.SWP391.horserace.auth.dto.VerifyCodeRequest;
import com.SWP391.horserace.auth.service.AuthService;
import com.SWP391.horserace.auth.service.PasswordResetService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    /** POST /api/v1/auth/login — email + password → access + refresh tokens. */
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                           HttpServletRequest httpRequest) {
        AuthResponse data = authService.login(request.email(), request.password(), userAgent(httpRequest));
        return ApiResponse.<AuthResponse>builder().success(true).message("Login successful").data(data).build();
    }

    /** POST /api/v1/auth/google — Google ID token → access + refresh tokens. */
    @PostMapping("/google")
    public ApiResponse<AuthResponse> google(@Valid @RequestBody GoogleLoginRequest request,
                                            HttpServletRequest httpRequest) {
        AuthResponse data = authService.loginWithGoogle(request.idToken(), userAgent(httpRequest));
        return ApiResponse.<AuthResponse>builder().success(true).message("Login successful").data(data).build();
    }

    /** POST /api/v1/auth/refresh — rotate refresh token, get a new access token. */
    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request,
                                             HttpServletRequest httpRequest) {
        AuthResponse data = authService.refresh(request.refreshToken(), userAgent(httpRequest));
        return ApiResponse.<AuthResponse>builder().success(true).message("Token refreshed").data(data).build();
    }

    /** POST /api/v1/auth/logout — revoke a refresh token. */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.<Void>builder().success(true).message("Logged out").build();
    }

    // =========================================================
    // Registration endpoints
    // =========================================================

    /**
     * POST /api/v1/auth/register/spectator
     * <p>Creates a SPECTATOR account and immediately issues tokens.
     * Required fields: fullName, email, password, confirmPassword.
     */
    @PostMapping("/register/spectator")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> registerSpectator(@Valid @RequestBody RegisterSpectatorRequest request,
                                                       HttpServletRequest httpRequest) {
        AuthResponse data = authService.registerSpectator(request, userAgent(httpRequest));
        return ApiResponse.<AuthResponse>builder()
                .success(true)
                .message("Spectator account created successfully")
                .data(data)
                .build();
    }

    /**
     * POST /api/v1/auth/register/owner
     * <p>Creates a HORSE_OWNER account and immediately issues tokens.
     * Required fields: fullName, email, password, confirmPassword.
     */
    @PostMapping("/register/owner")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> registerOwner(@Valid @RequestBody RegisterOwnerRequest request,
                                                   HttpServletRequest httpRequest) {
        AuthResponse data = authService.registerOwner(request, userAgent(httpRequest));
        return ApiResponse.<AuthResponse>builder()
                .success(true)
                .message("Owner account created successfully")
                .data(data)
                .build();
    }

    /**
     * POST /api/v1/auth/register/jockey
     * <p>Creates a JOCKEY account and immediately issues tokens.
     * Required fields: firstName, lastName, email, password, confirmPassword.
     */
    @PostMapping("/register/jockey")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> registerJockey(@Valid @RequestBody RegisterJockeyRequest request,
                                                    HttpServletRequest httpRequest) {
        AuthResponse data = authService.registerJockey(request, userAgent(httpRequest));
        return ApiResponse.<AuthResponse>builder()
                .success(true)
                .message("Jockey account created successfully")
                .data(data)
                .build();
    }

    // =========================================================
    // Password reset endpoints
    // =========================================================

    /** POST /api/v1/auth/forgot-password — send a 6-digit reset code to the registered email. */
    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.forgotPassword(request.email());
        return ApiResponse.<Void>builder()
                .success(true)
                .message("If that email is registered, a reset code has been sent")
                .build();
    }

    /** POST /api/v1/auth/resend-code — invalidate old code and send a fresh 6-digit code. */
    @PostMapping("/resend-code")
    public ApiResponse<Void> resendCode(@Valid @RequestBody ResendCodeRequest request) {
        passwordResetService.resendCode(request.email());
        return ApiResponse.<Void>builder()
                .success(true)
                .message("If that email is registered, a new reset code has been sent")
                .build();
    }

    /** POST /api/v1/auth/verify-code — validate the 6-digit code (without consuming it). */
    @PostMapping("/verify-code")
    public ApiResponse<Void> verifyCode(@Valid @RequestBody VerifyCodeRequest request) {
        passwordResetService.verifyCode(request.email(), request.code());
        return ApiResponse.<Void>builder()
                .success(true)
                .message("Verification code is valid")
                .build();
    }

    /** POST /api/v1/auth/reset-password — verify code + set new password. */
    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(
                request.email(), request.code(), request.newPassword(), request.confirmPassword());
        return ApiResponse.<Void>builder()
                .success(true)
                .message("Password has been reset successfully")
                .build();
    }

    private String userAgent(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.USER_AGENT);
    }
}
