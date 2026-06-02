package com.SWP391.horserace.auth.controller;

import com.SWP391.horserace.auth.dto.AuthResponse;
import com.SWP391.horserace.auth.dto.GoogleLoginRequest;
import com.SWP391.horserace.auth.dto.LoginRequest;
import com.SWP391.horserace.auth.dto.LogoutRequest;
import com.SWP391.horserace.auth.dto.RefreshRequest;
import com.SWP391.horserace.auth.dto.RegisterRequest;
import com.SWP391.horserace.auth.dto.VerifyEmailRequest;
import com.SWP391.horserace.auth.dto.ResendVerificationRequest;
import com.SWP391.horserace.auth.service.AuthService;
import com.SWP391.horserace.shared.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

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

    /** POST /api/v1/auth/register — register a new user in INACTIVE state. */
    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ApiResponse.<Void>builder().success(true).message("Registration successful. Please check your email to verify your account.").build();
    }

    /** POST /api/v1/auth/verify-email — verify user's email using token. */
    @PostMapping("/verify-email")
    public ApiResponse<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
        return ApiResponse.<Void>builder().success(true).message("Email verified successfully. You can now login.").build();
    }

    /** POST /api/v1/auth/resend-verification — resend email verification token. */
    @PostMapping("/resend-verification")
    public ApiResponse<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerificationEmail(request.email());
        return ApiResponse.<Void>builder().success(true).message("Verification email resent successfully. Please check your email.").build();
    }

    private String userAgent(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.USER_AGENT);
    }
}
