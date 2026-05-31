package com.SWP391.horserace.security;

import com.SWP391.horserace.shared.dto.ApiResponse;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Returns a JSON {@link ApiResponse} 403 when an authenticated user lacks the required role. */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ErrorCode ec = ErrorCode.UNAUTHORIZED;
        response.setStatus(ec.getStatusCode().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> body = ApiResponse.<Void>builder()
                .success(false)
                .message(ec.getMessage())
                .build();
        objectMapper.writeValue(response.getWriter(), body);
    }
}
