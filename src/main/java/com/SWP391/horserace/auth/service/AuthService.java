package com.SWP391.horserace.auth.service;
import com.SWP391.horserace.auth.dto.AuthResponse;
import com.SWP391.horserace.auth.dto.RegisterJockeyRequest;
import com.SWP391.horserace.auth.dto.RegisterOwnerRequest;
import com.SWP391.horserace.auth.dto.RegisterResponse;
import com.SWP391.horserace.auth.dto.RegisterSpectatorRequest;
import org.springframework.web.multipart.MultipartFile;

public interface AuthService {

    AuthResponse login(String email, String rawPassword, String userAgent);

    AuthResponse loginWithGoogle(String idToken, String userAgent);

    AuthResponse refresh(String rawRefreshToken, String userAgent);

    void logout(String rawRefreshToken);

    /** Register a new spectator account. Auto-issues tokens (no separate login needed). */
    AuthResponse registerSpectator(RegisterSpectatorRequest request, String userAgent);

    /** Register a new horse-owner account. Auto-issues tokens (no separate login needed). */
    AuthResponse registerOwner(RegisterOwnerRequest request, String userAgent);

    /**
     * Register a new jockey account. Unlike spectator/owner, a jockey is created in PENDING status
     * and must be approved by a referee before they can log in — so NO tokens are issued here.
     * The (optional) licence and fitness-certificate files are stored as sensitive attachments for
     * the referee to review.
     */
    RegisterResponse registerJockey(RegisterJockeyRequest request,
                                    MultipartFile licenseFile, MultipartFile fitnessFile);
}
