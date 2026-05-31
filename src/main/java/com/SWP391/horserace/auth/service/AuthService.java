package com.SWP391.horserace.auth.service;
import com.SWP391.horserace.auth.dto.AuthResponse;
import com.SWP391.horserace.auth.dto.RegisterRequest;
import com.SWP391.horserace.auth.dto.RegisterResponse;



public interface AuthService {

    AuthResponse login(String email, String rawPassword, String userAgent);

    AuthResponse loginWithGoogle(String idToken, String userAgent);

    AuthResponse refresh(String rawRefreshToken, String userAgent);

    void logout(String rawRefreshToken);

    RegisterResponse register(RegisterRequest request);
}
