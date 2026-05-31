package com.SWP391.horserace.security;

import com.SWP391.horserace.shared.dto.ApiResponse;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Returns a JSON {@link ApiResponse} 401 when an unauthenticated request hits a protected route. */
@Component
@RequiredArgsConstructor
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorCode ec = ErrorCode.UNAUTHENTICATED;
        response.setStatus(ec.getStatusCode().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> body = ApiResponse.<Void>builder()
                .success(false)
                .message(ec.getMessage())
                .build();
        objectMapper.writeValue(response.getWriter(), body);
    }
}
