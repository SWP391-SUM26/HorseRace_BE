package com.SWP391.horserace.auth.service.impl;

import com.SWP391.horserace.auth.dto.AuthResponse;
import com.SWP391.horserace.auth.dto.RegisterJockeyRequest;
import com.SWP391.horserace.auth.dto.RegisterOwnerRequest;
import com.SWP391.horserace.auth.dto.RegisterSpectatorRequest;
import com.SWP391.horserace.auth.service.AuthService;
import com.SWP391.horserace.auth.service.GoogleTokenVerifier;
import com.SWP391.horserace.auth.service.GoogleTokenVerifier.GooglePrincipal;
import com.SWP391.horserace.auth.service.JwtService;
import com.SWP391.horserace.auth.service.RefreshTokenService;
import com.SWP391.horserace.roles.entity.Role;
import com.SWP391.horserace.roles.repository.RoleRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.users.entity.KycStatus;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.entity.UserStatus;
import com.SWP391.horserace.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** Role granted to brand-new users created on first Google sign-in. */
    private static final String DEFAULT_GOOGLE_ROLE = "SPECTATOR";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final GoogleTokenVerifier googleTokenVerifier;

    // =========================================================
    // Login / token management
    // =========================================================

    @Override
    @Transactional
    public AuthResponse login(String email, String rawPassword, String userAgent) {
        String normalizedEmail = email != null ? email.trim().toLowerCase() : null;
        User user = userRepository.findByEmailAndDeletedFalse(normalizedEmail)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }
        ensureActive(user);
        return issueTokens(user, userAgent);
    }

    @Override
    @Transactional
    public AuthResponse loginWithGoogle(String idToken, String userAgent) {
        GooglePrincipal principal = googleTokenVerifier.verify(idToken);
        User user = userRepository.findByEmailAndDeletedFalse(principal.email())
                .orElseGet(() -> provisionGoogleUser(principal));
        ensureActive(user);
        return issueTokens(user, userAgent);
    }

    @Override
    @Transactional
    public AuthResponse refresh(String rawRefreshToken, String userAgent) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefreshToken, userAgent);
        User user = rotation.user();
        ensureActive(user);
        String accessToken = jwtService.generateAccessToken(user);
        return toResponse(user, accessToken, rotation.rawToken());
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    // =========================================================
    // Registration — Spectator
    // =========================================================

    /**
     * Registers a new SPECTATOR account.
     *
     * <p>Validates email uniqueness and password match, then saves the user and
     * immediately issues access + refresh tokens (no separate login step needed).
     */
    @Override
    @Transactional
    public AuthResponse registerSpectator(RegisterSpectatorRequest request, String userAgent) {
        String normalizedEmail = request.email().trim().toLowerCase();
        validateEmailAvailable(normalizedEmail);
        validatePasswordMatch(request.password(), request.confirmPassword());

        Role role = lookupRole("SPECTATOR");

        User user = User.builder()
                .role(role)
                .userCode(generateUserCode())
                .fullName(request.fullName().trim())
                .email(normalizedEmail)
                .phone(request.phone())
                .passwordHash(passwordEncoder.encode(request.password()))
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.PENDING)
                .build();

        userRepository.save(user);
        return issueTokens(user, userAgent);
    }

    // =========================================================
    // Registration — Horse Owner
    // =========================================================

    /**
     * Registers a new HORSE_OWNER account.
     *
     * <p>Stores fullName, email, contactNumber, and avatarUrl in {@code app_user}.
     * Extended profile fields (stableName, bio, primaryRegion) will be persisted in a
     * dedicated {@code owner_profile} table when that module is built.
     */
    @Override
    @Transactional
    public AuthResponse registerOwner(RegisterOwnerRequest request, String userAgent) {
        String normalizedEmail = request.email().trim().toLowerCase();
        validateEmailAvailable(normalizedEmail);
        validatePasswordMatch(request.password(), request.confirmPassword());

        Role role = lookupRole("HORSE_OWNER");

        User user = User.builder()
                .role(role)
                .userCode(generateUserCode())
                .fullName(request.fullName().trim())
                .email(normalizedEmail)
                .phone(request.contactNumber())
                .avatarUrl(request.avatarUrl())
                .passwordHash(passwordEncoder.encode(request.password()))
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.PENDING)
                .build();

        userRepository.save(user);
        return issueTokens(user, userAgent);
    }

    // =========================================================
    // Registration — Jockey
    // =========================================================

    /**
     * Registers a new JOCKEY account.
     *
     * <p>firstName + lastName are combined into {@code fullName}.
     * Extended profile fields (age, weight, nationality, yearsActive, ridingStyle,
     * jockeyLicenseUrl, fitnessCertificateUrl) will be persisted in a dedicated
     * {@code jockey_profile} table when that module is built.
     */
    @Override
    @Transactional
    public AuthResponse registerJockey(RegisterJockeyRequest request, String userAgent) {
        String normalizedEmail = request.email().trim().toLowerCase();
        validateEmailAvailable(normalizedEmail);
        validatePasswordMatch(request.password(), request.confirmPassword());

        Role role = lookupRole("JOCKEY");

        String fullName = (request.firstName() + " " + request.lastName()).trim();

        User user = User.builder()
                .role(role)
                .userCode(generateUserCode())
                .fullName(fullName)
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.PENDING)
                .build();

        userRepository.save(user);
        return issueTokens(user, userAgent);
    }

    // =========================================================
    // Private helpers
    // =========================================================

    private AuthResponse issueTokens(User user, String userAgent) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.issue(user, userAgent);
        return toResponse(user, accessToken, refreshToken);
    }

    private AuthResponse toResponse(User user, String accessToken, String refreshToken) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresInSeconds(jwtService.getAccessTokenTtlMs() / 1000)
                .userId(user.getUserId())
                .email(user.getEmail())
                .role(user.getRole() != null ? user.getRole().getRoleCode() : null)
                .build();
    }

    private void ensureActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.ACCOUNT_INACTIVE);
        }
    }

    /** Throws {@link ErrorCode#EMAIL_ALREADY_EXISTS} if the email is already taken. */
    private void validateEmailAvailable(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    /** Throws {@link ErrorCode#PASSWORD_MISMATCH} if the two password strings differ. */
    private void validatePasswordMatch(String password, String confirmPassword) {
        if (!password.equals(confirmPassword)) {
            throw new AppException(ErrorCode.PASSWORD_MISMATCH);
        }
    }

    /** Looks up a {@link Role} by code; throws {@link ErrorCode#ROLE_NOT_EXISTED} if missing. */
    private Role lookupRole(String roleCode) {
        return roleRepository.findByRoleCode(roleCode)
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_EXISTED));
    }

    /** Generates a short, human-readable user code: {@code USR-XXXXXXXX}. */
    private String generateUserCode() {
        return "USR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private User provisionGoogleUser(GooglePrincipal principal) {
        Role role = lookupRole(DEFAULT_GOOGLE_ROLE);

        User user = User.builder()
                .role(role)
                .userCode(generateUserCode())
                .fullName(principal.name())
                .email(principal.email())
                // Random un-guessable hash: Google users authenticate via Google, not this password.
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.PENDING)
                .build();
        return userRepository.save(user);
    }
}
