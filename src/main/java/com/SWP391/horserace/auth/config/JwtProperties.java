package com.SWP391.horserace.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Binds the {@code app.jwt.*} properties. */
@Component
@ConfigurationProperties(prefix = "app.jwt")
@Getter
@Setter
public class JwtProperties {

    /** Base64-encoded HMAC secret (>= 256 bits for HS256). */
    private String secret;

    /** Token issuer claim. */
    private String issuer = "horserace";

    /** Access-token lifetime in milliseconds. */
    private long accessTokenTtlMs = 900_000L;       // 15 min

    /** Refresh-token lifetime in milliseconds. */
    private long refreshTokenTtlMs = 604_800_000L;  // 7 days
}
