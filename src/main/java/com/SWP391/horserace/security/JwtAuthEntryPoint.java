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

    /** Set only when the 401 means the session itself is dead. Read by the FE axios interceptor. */
    public static final String SESSION_INVALID_HEADER = "X-Session-Invalid";

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorCode ec = ErrorCode.UNAUTHENTICATED;
        response.setStatus(ec.getStatusCode().value());
        // Marks THIS 401 as "the credential is gone/expired", as opposed to the ~62 service-layer
        // `AppException(UNAUTHENTICATED)` guards that also surface as 401 but mean a business
        // precondition failed. Without the distinction the client cannot tell them apart and ends
        // up destroying a perfectly valid session. This entry point is the only place Spring
        // rejects a request for lack of authentication, so it is the only place the flag belongs.
        response.setHeader(SESSION_INVALID_HEADER, "1");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> body = ApiResponse.<Void>builder()
                .success(false)
                .message(ec.getMessage())
                .build();
        objectMapper.writeValue(response.getWriter(), body);
    }
}
