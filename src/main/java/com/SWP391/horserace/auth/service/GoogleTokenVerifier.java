package com.SWP391.horserace.auth.service;

import com.SWP391.horserace.shared.exception.AppException;
import com.SWP391.horserace.shared.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Verifies a Google ID token by calling Google's tokeninfo endpoint, then checking that the
 * token's audience matches our configured OAuth client id. Google validates the token's
 * signature and expiry server-side, so no Google client library / extra dependency is needed.
 *
 * <p>Flow: the SPA signs the user in with Google, obtains an ID token, and POSTs it to
 * {@code /api/v1/auth/google}. This class turns it into a trusted {@link GooglePrincipal}.
 */
@Service
@Slf4j
public class GoogleTokenVerifier {

    private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo";

    private final String expectedAudience;
    private final RestClient restClient;

    public GoogleTokenVerifier(@Value("${app.google.client-id}") String expectedAudience) {
        this.expectedAudience = expectedAudience;
        this.restClient = RestClient.create();
    }

    /** Verified, trusted claims extracted from a Google ID token. */
    public record GooglePrincipal(String email, String name, String subject, boolean emailVerified) {}

    public GooglePrincipal verify(String idToken) {
        GoogleTokenInfo info;
        try {
            info = restClient.get()
                    .uri(TOKENINFO_URL + "?id_token={t}", idToken)
                    .retrieve()
                    .body(GoogleTokenInfo.class);
        } catch (Exception ex) {
            log.warn("Google tokeninfo call failed: {}", ex.getMessage());
            throw new AppException(ErrorCode.GOOGLE_AUTH_FAILED, "Could not verify Google token");
        }

        if (info == null || info.email() == null) {
            throw new AppException(ErrorCode.GOOGLE_AUTH_FAILED, "Google token had no email");
        }
        if (!expectedAudience.equals(info.aud())) {
            log.warn("Google token audience mismatch: got {}", info.aud());
            throw new AppException(ErrorCode.GOOGLE_AUTH_FAILED, "Google token audience mismatch");
        }
        boolean emailVerified = "true".equalsIgnoreCase(info.emailVerified());
        if (!emailVerified) {
            throw new AppException(ErrorCode.GOOGLE_AUTH_FAILED, "Google email is not verified");
        }
        return new GooglePrincipal(info.email(), info.name(), info.sub(), true);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GoogleTokenInfo(
            String aud,
            String sub,
            String email,
            @JsonProperty("email_verified") String emailVerified,
            String name) {
    }
}
