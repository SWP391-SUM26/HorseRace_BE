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
    INVITATION_NOT_ACCEPTED(2011, "Only ACCEPTED rides can be withdrawn", HttpStatus.BAD_REQUEST),

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
    MICROCHIP_EXISTED(5004, "Microchip number already exists", HttpStatus.CONFLICT),
    NOT_HORSE_OWNER(5005, "You are not the owner of this horse", HttpStatus.FORBIDDEN),
    HORSE_NO_APPROVED_REGISTRATION(5006, "Horse has no approved registration for this race's tournament", HttpStatus.BAD_REQUEST),

    // ---- file upload / storage ----
    FILE_EMPTY(6001, "Uploaded file is empty", HttpStatus.BAD_REQUEST),
    INVALID_FILE_TYPE(6002, "Unsupported file type. Allowed: PNG, JPEG, WEBP, GIF", HttpStatus.BAD_REQUEST),
    FILE_TOO_LARGE(6003, "File exceeds the maximum allowed size (5MB)", HttpStatus.PAYLOAD_TOO_LARGE),
    FILE_STORAGE_FAILED(6004, "Failed to store the uploaded file", HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_NOT_FOUND(6005, "File not found", HttpStatus.NOT_FOUND),

    // ---- registration management ----
    REGISTRATION_NOT_FOUND(7001, "Registration not found", HttpStatus.NOT_FOUND),
    REGISTRATION_ALREADY_EXISTS(7002, "This horse is already registered for this tournament", HttpStatus.CONFLICT),
    REGISTRATION_INVALID_STATUS(7003, "Invalid status transition for this registration", HttpStatus.BAD_REQUEST),
    NOT_REGISTRATION_OWNER(7004, "You are not the owner of this registration", HttpStatus.FORBIDDEN),
    TOURNAMENT_NOT_ACCEPTING_REGISTRATION(7005, "Tournament is not open for registration", HttpStatus.BAD_REQUEST),

    // ---- race management ----
    RACE_CODE_EXISTED(8001, "Race code already exists", HttpStatus.CONFLICT),
    RACE_INVALID_STATUS(8002, "Invalid status transition for this race", HttpStatus.BAD_REQUEST),
    RACE_NOT_OPEN_FOR_ENTRY(8003, "Race is not open for participant entry", HttpStatus.BAD_REQUEST),
    RACE_TOURNAMENT_MISMATCH(8004, "Registration and race belong to different tournaments", HttpStatus.BAD_REQUEST),
    RACE_FULL(8005, "Race has reached its maximum number of participants", HttpStatus.BAD_REQUEST),
    REGISTRATION_NOT_APPROVED(8006, "Registration is not approved", HttpStatus.BAD_REQUEST),

    // ---- prediction system ----
    PREDICTION_NOT_FOUND(9001, "Prediction not found", HttpStatus.NOT_FOUND),
    PREDICTION_ALREADY_EXISTS(9002, "You have already made this type of prediction for this race and entry", HttpStatus.CONFLICT),
    PREDICTION_RACE_NOT_OPEN(9003, "Race is not open for predictions", HttpStatus.BAD_REQUEST),
    PREDICTION_ENTRY_NOT_FOUND(9004, "Race entry not found", HttpStatus.NOT_FOUND),
    PREDICTION_ENTRY_MISMATCH(9005, "The predicted entry does not belong to the specified race", HttpStatus.BAD_REQUEST),
    PREDICTION_CANNOT_CANCEL(9006, "Prediction cannot be cancelled at this stage", HttpStatus.BAD_REQUEST),
    IDEMPOTENCY_KEY_EXISTED(9007, "Idempotency key already exists", HttpStatus.CONFLICT),
    BETTING_POOL_CLOSED(9008, "Betting pool is not open", HttpStatus.BAD_REQUEST),

    // ---- referee management ----
    REPORT_NOT_FOUND(9101, "Referee report not found", HttpStatus.NOT_FOUND),
    REPORT_INVALID_STATUS(9102, "Invalid status transition for this report", HttpStatus.BAD_REQUEST),

    // ---- reward system ----
    REWARD_NOT_FOUND(9201, "Reward not found", HttpStatus.NOT_FOUND),
    REWARD_ALREADY_CLAIMED(9202, "Reward has already been claimed", HttpStatus.BAD_REQUEST),
    REWARD_EXPIRED(9203, "Reward has expired", HttpStatus.BAD_REQUEST),
    NOT_REWARD_OWNER(9204, "You do not own this reward", HttpStatus.FORBIDDEN),

    // ---- pre-race inspection (FE-v2 §2) ----
    INSPECTION_ENTRY_RACE_MISMATCH(9401, "The entry does not belong to the specified race", HttpStatus.BAD_REQUEST),
    INSPECTION_CONFIRM_REQUIRED(9402, "Submission must be confirmed", HttpStatus.BAD_REQUEST),

    // ---- results record/read/edit/certify (FE-v2 §5) ----
    RESULT_NOT_FOUND(9501, "Race result not found", HttpStatus.NOT_FOUND),
    RESULT_INQUIRIES_UNRESOLVED(9502, "All inquiries must be resolved before certification", HttpStatus.BAD_REQUEST),
    RESULT_ENTRY_RACE_MISMATCH(9503, "The entry does not belong to the specified race", HttpStatus.BAD_REQUEST),

    // ---- violations / inquiries (FE-v2 §3) ----
    VIOLATION_NOT_FOUND(9601, "Violation not found", HttpStatus.NOT_FOUND),
    VIOLATION_ENTRY_RACE_MISMATCH(9602, "The entry does not belong to the specified race", HttpStatus.BAD_REQUEST),
    VIOLATION_ALREADY_RULED(9603, "This violation has already been ruled on", HttpStatus.BAD_REQUEST),

    // ---- attachments (FE-v2 §6) ----
    ATTACHMENT_INVALID_OWNER_TYPE(9701, "Invalid ownerEntityType. Allowed: RACE_RESULT, VIOLATION, RACE", HttpStatus.BAD_REQUEST),
    ATTACHMENT_INVALID_SENSITIVITY(9702, "Invalid sensitivityLevel. Allowed: PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED", HttpStatus.BAD_REQUEST),

    // ---- referee applicant onboarding (FE-v2 Registration Approval) ----
    APPLICATION_NOT_FOUND(9301, "Membership application not found", HttpStatus.NOT_FOUND),
    APPLICATION_ALREADY_DECIDED(9302, "Application has already been decided", HttpStatus.BAD_REQUEST),
    APPLICATION_INVALID_STATUS(9303, "Invalid status transition for this application", HttpStatus.BAD_REQUEST),
    APPLICATION_ROLE_NOT_FOUND(9304, "Target role for the requested role mapping was not found", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus statusCode;

    ErrorCode(int code, String message, HttpStatus statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }
}
