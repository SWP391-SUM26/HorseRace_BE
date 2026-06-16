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
    WEAK_PASSWORD(1015, "Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character", HttpStatus.BAD_REQUEST),
    INVALID_PHONE_FORMAT(1016, "Phone number must be a valid Vietnamese phone number", HttpStatus.BAD_REQUEST),

    // ---- authentication / tokens ----
    INVALID_CREDENTIALS(1007, "Invalid email or password", HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(1008, "Invalid or expired access token", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_INVALID(1009, "Invalid or expired refresh token", HttpStatus.UNAUTHORIZED),
    ACCOUNT_INACTIVE(1010, "Account is not active", HttpStatus.FORBIDDEN),
    GOOGLE_AUTH_FAILED(1011, "Google authentication failed", HttpStatus.UNAUTHORIZED),
    ROLE_NOT_EXISTED(1012, "Default role not found", HttpStatus.INTERNAL_SERVER_ERROR),

    // ---- email change (verified flow) ----
    EMAIL_EXISTED(1013, "Email is already in use", HttpStatus.CONFLICT),
    EMAIL_SAME_AS_CURRENT(1014, "New email must differ from your current email", HttpStatus.BAD_REQUEST),
    EMAIL_VERIFICATION_INVALID(1015, "Invalid or expired verification code", HttpStatus.BAD_REQUEST),

    // ---- registration ----
    EMAIL_ALREADY_EXISTS(1016, "Email is already registered", HttpStatus.CONFLICT),
    PASSWORD_MISMATCH(1017, "Passwords do not match", HttpStatus.BAD_REQUEST),

    // ---- password reset ----
    RESET_CODE_INVALID(1018, "Invalid or expired reset code", HttpStatus.BAD_REQUEST),
    RESET_CODE_USED(1019, "Reset code has already been used", HttpStatus.BAD_REQUEST),
    RESET_CODE_COOLDOWN(1020, "Please wait before requesting a new code", HttpStatus.TOO_MANY_REQUESTS),
    EMAIL_SEND_FAILED(1021, "Failed to send email", HttpStatus.INTERNAL_SERVER_ERROR),
    PASSWORD_TOO_WEAK(1022, "Password must be at least 8 characters and contain 1 number and 1 symbol", HttpStatus.BAD_REQUEST),

    // ---- jockey ----
    JOCKEY_NOT_FOUND(2001, "Jockey profile not found", HttpStatus.NOT_FOUND),

    // ---- jockey assignment / invitation ----
    ASSIGNMENT_NOT_FOUND(2002, "Jockey assignment not found", HttpStatus.NOT_FOUND),
    ENTRY_NOT_FOUND(2003, "Race entry not found", HttpStatus.NOT_FOUND),
    ENTRY_ALREADY_ASSIGNED(2004, "This race entry already has a jockey assigned", HttpStatus.CONFLICT),
    JOCKEY_ALREADY_INVITED(2005, "This jockey already has a pending invitation for this entry", HttpStatus.CONFLICT),
    INVITATION_NOT_PENDING(2006, "Invitation is not in INVITED status", HttpStatus.BAD_REQUEST),
    INVITATION_CANNOT_CANCEL(2007, "Only INVITED status invitations can be cancelled", HttpStatus.BAD_REQUEST),
    NOT_INVITATION_OWNER(2008, "You are not the owner who sent this invitation", HttpStatus.FORBIDDEN),
    NOT_INVITED_JOCKEY(2009, "You are not the jockey invited for this assignment", HttpStatus.FORBIDDEN),
    OWNER_NOT_MATCH(2010, "You are not the owner of this horse entry", HttpStatus.FORBIDDEN),

    // ---- tournament ----
    TOURNAMENT_NOT_FOUND(3001, "Tournament not found", HttpStatus.NOT_FOUND),
    TOURNAMENT_CODE_EXISTED(3002, "Tournament code already existed", HttpStatus.CONFLICT),
    TOURNAMENT_INVALID_STATUS(3003, "Invalid status transition for tournament", HttpStatus.BAD_REQUEST),

    // ---- staffing / referee assignment ----
    STAFF_NOT_FOUND(4001, "Staff member not found", HttpStatus.NOT_FOUND),
    STAFF_NOT_REFEREE(4002, "User is not a referee", HttpStatus.BAD_REQUEST),
    RACE_NOT_FOUND(4003, "Race not found", HttpStatus.NOT_FOUND),
    REFEREE_ALREADY_ASSIGNED(4004, "Referee is already assigned to this race", HttpStatus.CONFLICT),
    REFEREE_ASSIGNMENT_NOT_FOUND(4005, "Referee assignment not found", HttpStatus.NOT_FOUND),
    ASSIGNMENT_ALREADY_REVOKED(4006, "Assignment has already been revoked", HttpStatus.BAD_REQUEST),
    STAFF_EMAIL_EXISTED(4007, "Email is already registered", HttpStatus.CONFLICT),

    // ---- horse management ----
    HORSE_NOT_FOUND(5001, "Horse not found", HttpStatus.NOT_FOUND),
    HORSE_NAME_REQUIRED(5002, "Horse name is required", HttpStatus.BAD_REQUEST),
    HORSE_CODE_EXISTED(5003, "Horse code already exists", HttpStatus.CONFLICT),
    MICROCHIP_EXISTED(5004, "Microchip number already exists", HttpStatus.CONFLICT),
    NOT_HORSE_OWNER(5005, "You are not the owner of this horse", HttpStatus.FORBIDDEN),

    // ---- file upload / storage ----
    FILE_EMPTY(6001, "Uploaded file is empty", HttpStatus.BAD_REQUEST),
    INVALID_FILE_TYPE(6002, "Unsupported file type. Allowed: PNG, JPEG, WEBP, GIF", HttpStatus.BAD_REQUEST),
    FILE_TOO_LARGE(6003, "File exceeds the maximum allowed size (5MB)", HttpStatus.PAYLOAD_TOO_LARGE),
    FILE_STORAGE_FAILED(6004, "Failed to store the uploaded file", HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_NOT_FOUND(6005, "File not found", HttpStatus.NOT_FOUND);

    private final int code;
    private final String message;
    private final HttpStatus statusCode;

    ErrorCode(int code, String message, HttpStatus statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }
}
