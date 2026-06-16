package com.SWP391.horserace.shared.exception;

import com.SWP391.horserace.shared.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<ApiResponse<Void>> handlingRuntimeException(Exception exception) {
        log.error("Exception: ", exception);
        // DEV: include actual exception details for debugging
        String debugMessage = ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage()
                + " [" + exception.getClass().getSimpleName() + ": " + exception.getMessage() + "]";
        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder()
                .success(false)
                .message(debugMessage)
                .build();
        return ResponseEntity.status(ErrorCode.UNCATEGORIZED_EXCEPTION.getStatusCode()).body(apiResponse);
    }

    @ExceptionHandler(value = AppException.class)
    public ResponseEntity<ApiResponse<Void>> handlingAppException(AppException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder()
                .success(false)
                .message(errorCode.getMessage())
                .build();
        return ResponseEntity.status(errorCode.getStatusCode()).body(apiResponse);
    }

    @ExceptionHandler(value = AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handlingAccessDeniedException(AccessDeniedException exception) {
        log.warn("Access denied: {}", exception.getMessage());
        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder()
                .success(false)
                .message(ErrorCode.UNAUTHORIZED.getMessage())
                .build();
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED.getStatusCode()).body(apiResponse);
    }

    @ExceptionHandler(value = AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handlingAuthenticationException(AuthenticationException exception) {
        log.warn("Authentication failed: {}", exception.getMessage());
        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder()
                .success(false)
                .message(ErrorCode.UNAUTHENTICATED.getMessage())
                .build();
        return ResponseEntity.status(ErrorCode.UNAUTHENTICATED.getStatusCode()).body(apiResponse);
    }

    @ExceptionHandler(value = MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handlingMaxUploadSize(MaxUploadSizeExceededException exception) {
        log.warn("Upload too large: {}", exception.getMessage());
        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder()
                .success(false)
                .message(ErrorCode.FILE_TOO_LARGE.getMessage())
                .build();
        return ResponseEntity.status(ErrorCode.FILE_TOO_LARGE.getStatusCode()).body(apiResponse);
    }

    /**
     * Malformed request body (e.g. an invalid enum value in JSON) or an unconvertible query/path
     * param (e.g. a bad enum/UUID bound via @ModelAttribute/@RequestParam) → 400, not a 500.
     */
    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            BindException.class})
    public ResponseEntity<ApiResponse<Void>> handlingBadInput(Exception exception) {
        log.warn("Bad request: {}", exception.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.<Void>builder()
                .success(false)
                .message("Invalid or malformed request")
                .build());
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handlingValidation(MethodArgumentNotValidException exception) {
        String enumKey = exception.getFieldError() != null ? exception.getFieldError().getDefaultMessage() : "INVALID_KEY";

        ErrorCode errorCode = ErrorCode.INVALID_KEY;
        String message = enumKey;
        
        try {
            errorCode = ErrorCode.valueOf(enumKey);
            message = errorCode.getMessage();
        } catch (IllegalArgumentException e) {
            // Fallback: If the default message is not a valid enum key, use it as a literal string
        }

        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder()
                .success(false)
                .message(message)
                .build();
        return ResponseEntity.status(errorCode.getStatusCode()).body(apiResponse);
    }
}
