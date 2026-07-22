package com.SWP391.horserace.auth.service.impl;

import com.SWP391.horserace.auth.dto.AuthResponse;
import com.SWP391.horserace.auth.dto.RegisterJockeyRequest;
import com.SWP391.horserace.auth.dto.RegisterOwnerRequest;
import com.SWP391.horserace.auth.dto.RegisterResponse;
import com.SWP391.horserace.auth.dto.RegisterSpectatorRequest;
import com.SWP391.horserace.auth.service.AuthService;
import com.SWP391.horserace.attachments.dto.AttachmentResponse;
import com.SWP391.horserace.attachments.service.AttachmentService;
import com.SWP391.horserace.jockeys.entity.JockeyProfile;
import com.SWP391.horserace.jockeys.repository.JockeyProfileRepository;
import com.SWP391.horserace.onboarding.entity.MembershipApplication;
import com.SWP391.horserace.onboarding.entity.RequestedRole;
import com.SWP391.horserace.onboarding.repository.MembershipApplicationRepository;
import com.SWP391.horserace.owner.entity.OwnerProfile;
import com.SWP391.horserace.owner.repository.OwnerProfileRepository;
import com.SWP391.horserace.auth.service.GoogleTokenVerifier;
import com.SWP391.horserace.auth.service.GoogleTokenVerifier.GooglePrincipal;
import com.SWP391.horserace.auth.service.JwtService;
import com.SWP391.horserace.auth.service.RefreshTokenService;
import com.SWP391.horserace.roles.entity.Role;
import com.SWP391.horserace.roles.repository.RoleRepository;
import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.SWP391.horserace.shared.util.PhoneUtil;
import com.SWP391.horserace.users.entity.KycStatus;
import com.SWP391.horserace.users.entity.User;
import com.SWP391.horserace.users.entity.UserStatus;
import com.SWP391.horserace.users.repository.UserRepository;
import com.SWP391.horserace.users.service.UserCodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** Role granted to brand-new users created on first Google sign-in. */
    private static final String DEFAULT_GOOGLE_ROLE = "SPECTATOR";

    private final UserRepository userRepository;
    private final UserCodeGenerator userCodeGenerator;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final JockeyProfileRepository jockeyProfileRepository;
    private final MembershipApplicationRepository membershipApplicationRepository;
    private final OwnerProfileRepository ownerProfileRepository;
    private final AttachmentService attachmentService;

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
        recordLogin(user);
        return issueTokens(user, userAgent);
    }

    @Override
    @Transactional
    public AuthResponse loginWithGoogle(String idToken, String userAgent) {
        GooglePrincipal principal = googleTokenVerifier.verify(idToken);
        User user = userRepository.findByEmailAndDeletedFalse(principal.email())
                .orElseGet(() -> provisionGoogleUser(principal));

        // Link the Google account on first Google sign-in for this email: persist the Google
        // subject id and mark the email verified (Google has already verified the address).
        boolean changed = false;
        if (user.getGoogleId() == null) {
            user.setGoogleId(principal.subject());
            changed = true;
        }
        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            changed = true;
        }
        if (changed) {
            user = userRepository.save(user);
        }

        ensureActive(user);
        recordLogin(user);
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
        // FR-11: normalize the phone (strip spaces/dashes) before the uniqueness check AND store it.
        String normalizedPhone = PhoneUtil.normalize(request.phone());
        validateEmailAvailable(normalizedEmail);
        validatePhoneAvailable(normalizedPhone);
        validatePasswordMatch(request.password(), request.confirmPassword());

        Role role = lookupRole("SPECTATOR");

        User user = User.builder()
                .role(role)
                .userCode(generateUserCode())
                .fullName(request.fullName().trim())
                .email(normalizedEmail)
                .phone(normalizedPhone)
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
     * <p>Stores fullName, email, contactNumber, avatarUrl in {@code app_user}, and the extended
     * fields (stableName, bio, primaryRegion) in {@code owner_profile}. Owner is ACTIVE immediately.
     */
    @Override
    @Transactional
    public AuthResponse registerOwner(RegisterOwnerRequest request, String userAgent) {
        String normalizedEmail = request.email().trim().toLowerCase();
        // FR-11: normalize the phone (strip spaces/dashes) before the uniqueness check AND store it.
        String normalizedPhone = PhoneUtil.normalize(request.contactNumber());
        validateEmailAvailable(normalizedEmail);
        validatePhoneAvailable(normalizedPhone);
        validatePasswordMatch(request.password(), request.confirmPassword());

        Role role = lookupRole("HORSE_OWNER");

        User user = User.builder()
                .role(role)
                .userCode(generateUserCode())
                .fullName(request.fullName().trim())
                .email(normalizedEmail)
                .phone(normalizedPhone)
                .avatarUrl(request.avatarUrl())
                .passwordHash(passwordEncoder.encode(request.password()))
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.PENDING)
                .build();

        userRepository.save(user);

        // Extended owner profile — persist the fields the form collects.
        OwnerProfile profile = OwnerProfile.builder()
                .ownerUser(user)
                .stableName(request.stableName())
                .primaryRegion(request.primaryRegion())
                .bio(request.bio())
                .build();
        ownerProfileRepository.save(profile);

        return issueTokens(user, userAgent);
    }

    // =========================================================
    // Registration — Jockey
    // =========================================================

    /**
     * Registers a new JOCKEY.
     *
     * <p>Unlike spectator/owner, a jockey does NOT get an active account or tokens immediately:
     * the account is created in {@link UserStatus#PENDING}, the extended profile is stored in
     * {@code jockey_profile}, and a {@link MembershipApplication} (requestedRole=JOCKEY, PENDING)
     * is filed so a referee can review and approve. Approval flips the user to ACTIVE
     * (see {@code RefereeApplicationServiceImpl.approve}). No {@link AuthResponse} is returned.
     */
    @Override
    @Transactional
    public RegisterResponse registerJockey(RegisterJockeyRequest request,
                                           MultipartFile licenseFile, MultipartFile fitnessFile) {
        String normalizedEmail = request.email().trim().toLowerCase();
        validateEmailAvailable(normalizedEmail);
        validatePasswordMatch(request.password(), request.confirmPassword());

        Role role = lookupRole("JOCKEY");
        String fullName = (request.firstName() + " " + request.lastName()).trim();

        // 1) PENDING account — cannot log in until a referee approves.
        User user = User.builder()
                .role(role)
                .userCode(generateUserCode())
                .fullName(fullName)
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .status(UserStatus.PENDING)
                .kycStatus(KycStatus.PENDING)
                .build();
        userRepository.save(user);

        // 2) Store the sensitive documents (licence / fitness cert) as RESTRICTED attachments,
        //    served later only through the auth-gated download route (never the public /files).
        String licenseUrl = storeJockeyDoc(user, licenseFile);
        String fitnessUrl = storeJockeyDoc(user, fitnessFile);

        // 3) Extended jockey profile (self-registration fields + document download paths).
        JockeyProfile profile = JockeyProfile.builder()
                .jockeyUser(user)
                .age(request.age())
                .nationality(request.nationality())
                .applicationRidingStyle(request.ridingStyle())
                .experienceYrs(request.yearsActive())
                .bodyWeight(toBodyWeightKg(request.weight()))
                .jockeyLicenseUrl(licenseUrl)
                .fitnessCertificateUrl(fitnessUrl)
                .build();
        jockeyProfileRepository.save(profile);

        // 3) Onboarding application for the referee queue (matched to the user by email on approval).
        MembershipApplication application = MembershipApplication.builder()
                .applicationCode(generateApplicationCode())
                .requestedRole(RequestedRole.JOCKEY)
                .fullName(fullName)
                .email(normalizedEmail)
                .build();
        membershipApplicationRepository.save(application);

        return RegisterResponse.builder()
                .userId(user.getUserId())
                .userCode(user.getUserCode())
                .fullName(fullName)
                .email(normalizedEmail)
                .role(role.getRoleCode())
                .build();
    }

    /**
     * Store the form's body weight (kg) into jockey_profile.body_weight (kg, 2dp); null-safe.
     * body_weight is kilograms across the system — the jockey self-profile edit and the admin
     * review both read/write it as kg — so registration must NOT convert (a lbs→kg conversion here
     * was what made the reviewed weight look "off" against the entered value).
     */
    private BigDecimal toBodyWeightKg(Double weightKg) {
        return weightKg == null ? null
                : BigDecimal.valueOf(weightKg).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Stores a jockey document as a RESTRICTED attachment owned by the jockey and returns the
     * auth-gated download path ({@code /api/v1/attachments/{id}/download}), or null if no file.
     */
    private String storeJockeyDoc(User user, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        AttachmentResponse att = attachmentService.upload(
                user.getUserId(), file, "JOCKEY_PROFILE", user.getUserId(), "RESTRICTED");
        return "/api/v1/attachments/" + att.getAttachmentId() + "/download";
    }

    // =========================================================
    // Private helpers
    // =========================================================

    /**
     * Stamps {@code last_login_at} on a successful interactive login (password / Google) and
     * persists it. Not called on token refresh or registration — only genuine login events.
     */
    private void recordLogin(User user) {
        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);
    }

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
        if (user.getStatus() == UserStatus.PENDING) {
            // Self-registered jockey awaiting referee approval — distinct, clearer error.
            throw new AppException(ErrorCode.ACCOUNT_PENDING_APPROVAL);
        }
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

    /** Throws {@link ErrorCode#PHONE_ALREADY_EXISTS} if a non-blank phone is already registered. */
    private void validatePhoneAvailable(String phone) {
        if (phone != null && !phone.isBlank() && userRepository.existsByPhone(phone.trim())) {
            throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
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

    /** Sequential user code (USR0009, USR0010...) — see {@link UserCodeGenerator}. */
    private String generateUserCode() {
        return userCodeGenerator.generate();
    }

    /** Generates a short, human-readable application code: {@code APP-XXXXXXXX}. */
    private String generateApplicationCode() {
        return "APP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
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
                .emailVerified(true)            // Google sign-in: email is already verified by Google
                .googleId(principal.subject())  // remember the Google account id
                .build();
        return userRepository.save(user);
    }
}
