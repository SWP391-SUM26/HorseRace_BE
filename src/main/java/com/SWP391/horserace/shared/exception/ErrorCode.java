package com.SWP391.horserace.shared.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEY(1001, "Invalid message key", HttpStatus.BAD_REQUEST),
    USER_EXISTED(1002, "User already existed", HttpStatus.BAD_REQUEST),
    USER_NOT_EXISTED(1003, "User not found", HttpStatus.NOT_FOUND),
    UNAUTHENTICATED(1004, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1005, "You do not have permission", HttpStatus.FORBIDDEN),
    INVALID_PASSWORD(1006, "Password must be at least 8 characters", HttpStatus.BAD_REQUEST),

    // ---- authentication / tokens ----
    INVALID_CREDENTIALS(1007, "Invalid email or password", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(1008, "Invalid or expired access token", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_INVALID(1009, "Invalid or expired refresh token", HttpStatus.UNAUTHORIZED),
    ACCOUNT_INACTIVE(1010, "Account is not active", HttpStatus.FORBIDDEN),
    GOOGLE_AUTH_FAILED(1011, "Google authentication failed", HttpStatus.UNAUTHORIZED),
    ROLE_NOT_EXISTED(1012, "Default role not found", HttpStatus.INTERNAL_SERVER_ERROR),

<<<<<<< Updated upstream
    // ---- registration ----
    EMAIL_ALREADY_EXISTS(1013, "Email is already registered", HttpStatus.CONFLICT),
    PASSWORD_MISMATCH(1014, "Password and confirm password do not match", HttpStatus.BAD_REQUEST);
=======
    // ---- password reset ----
    RESET_CODE_INVALID(1013, "Invalid or expired reset code", HttpStatus.BAD_REQUEST),
    RESET_CODE_USED(1014, "Reset code has already been used", HttpStatus.BAD_REQUEST),
    RESET_CODE_COOLDOWN(1015, "Please wait before requesting a new code", HttpStatus.TOO_MANY_REQUESTS),
    PASSWORD_MISMATCH(1016, "Passwords do not match", HttpStatus.BAD_REQUEST),
    EMAIL_SEND_FAILED(1017, "Failed to send email", HttpStatus.INTERNAL_SERVER_ERROR),
    PASSWORD_TOO_WEAK(1018, "Password must be at least 8 characters and contain 1 number and 1 symbol", HttpStatus.BAD_REQUEST);
>>>>>>> Stashed changes

    private final int code;
    private final String message;
    private final HttpStatus statusCode;

    ErrorCode(int code, String message, HttpStatus statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }
}
