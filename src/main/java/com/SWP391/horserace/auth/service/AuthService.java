package com.SWP391.horserace.auth.service;

import com.SWP391.horserace.auth.dto.AuthResponse;
import com.SWP391.horserace.auth.dto.RegisterJockeyRequest;
import com.SWP391.horserace.auth.dto.RegisterOwnerRequest;
import com.SWP391.horserace.auth.dto.RegisterSpectatorRequest;

public interface AuthService {

    AuthResponse login(String email, String rawPassword, String userAgent);

    AuthResponse loginWithGoogle(String idToken, String userAgent);

    AuthResponse refresh(String rawRefreshToken, String userAgent);

    void logout(String rawRefreshToken);

    /** Register a new spectator account. Auto-issues tokens (no separate login needed). */
    AuthResponse registerSpectator(RegisterSpectatorRequest request, String userAgent);

    /** Register a new horse-owner account. Auto-issues tokens (no separate login needed). */
    AuthResponse registerOwner(RegisterOwnerRequest request, String userAgent);

    /** Register a new jockey account. Auto-issues tokens (no separate login needed). */
    AuthResponse registerJockey(RegisterJockeyRequest request, String userAgent);
}
