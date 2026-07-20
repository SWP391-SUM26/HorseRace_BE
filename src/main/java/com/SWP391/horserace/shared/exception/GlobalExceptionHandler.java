package com.SWP391.horserace.shared.exception;

import com.SWP391.horserace.shared.dto.ApiResponse;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<ApiResponse<Void>> handlingRuntimeException(Exception exception) {
        // FR-10: full detail (class/message/stack) is logged server-side ONLY. The client body
        // carries the generic message so an uncaught exception can never leak internals/secrets.
        log.error("Exception: ", exception);
        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder()
                .success(false)
                .message(ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage())
                .build();
        return ResponseEntity.status(ErrorCode.UNCATEGORIZED_EXCEPTION.getStatusCode()).body(apiResponse);
    }

    @ExceptionHandler(value = AppException.class)
    public ResponseEntity<ApiResponse<Void>> handlingAppException(AppException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        // Prefer the exception's own message: it equals the ErrorCode default for single-arg
        // AppException, but carries the specific detail for AppException(errorCode, message)
        // (e.g. RACE_NOT_READY listing which conditions are unmet).
        String message = exception.getMessage() != null ? exception.getMessage() : errorCode.getMessage();
        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder()
                .success(false)
                .message(message)
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
    /**
     * A DB constraint slipped past the service-level checks (e.g. reusing the microchip of a
     * soft-deleted horse, or a rare generated-code race) → 409 Conflict, not a generic 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handlingDataIntegrity(DataIntegrityViolationException exception) {
        log.warn("Data integrity violation: {}", exception.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.<Void>builder()
                .success(false)
                .message("The request conflicts with existing data (duplicate or constraint violation)")
                .build());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse<Void>> handlingBadInput(Exception exception) {
        log.warn("Bad request: {}", exception.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.<Void>builder()
                .success(false)
                .message("Invalid or malformed request")
                .build());
    }

    /**
     * Merged field-level validation handler (FR-09). Covers BOTH
     * {@link MethodArgumentNotValidException} (@Valid @RequestBody) and plain {@link BindException}
     * (@ModelAttribute binding) — both extend {@code BindException}, so one param serves both. Maps
     * EVERY failing field (not just the first) to a {@link FieldErrorItem}, resolving each message via
     * {@link #resolveMessage(String)} so DTO annotations carrying an {@code ErrorCode} enum name (e.g.
     * {@code message = "WEAK_PASSWORD"}) still resolve to that code's message while literal messages
     * pass through. Status is ALWAYS 400 (a validation failure is a bad request).
     *
     * <p>Note: this maps only {@code getBindingResult().getFieldErrors()} — class-level
     * ({@code ObjectError}) cross-field constraints are not surfaced here (none exist today).
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResponse<List<FieldErrorItem>>> handlingValidation(BindException exception) {
        List<FieldErrorItem> items = exception.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldErrorItem(fe.getField(), resolveMessage(fe.getDefaultMessage())))
                .toList();

        String message = items.isEmpty() ? "Validation failed" : items.get(0).message();
        return ResponseEntity.badRequest().body(ApiResponse.<List<FieldErrorItem>>builder()
                .success(false)
                .message(message)
                .data(items)
                .build());
    }

    /**
     * Resolve one field's default message: {@code null} → the generic INVALID_KEY message; an
     * {@code ErrorCode} enum name → that code's message; otherwise the literal string as-is.
     */
    private String resolveMessage(String raw) {
        if (raw == null) {
            return ErrorCode.INVALID_KEY.getMessage();
        }
        try {
            return ErrorCode.valueOf(raw).getMessage();
        } catch (IllegalArgumentException e) {
            return raw;
        }
    }
}
