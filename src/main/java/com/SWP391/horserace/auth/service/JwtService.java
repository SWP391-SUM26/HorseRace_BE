package com.SWP391.horserace.auth.service;

import com.SWP391.horserace.auth.config.JwtProperties;
import com.SWP391.horserace.users.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and parses stateless HS256 access tokens. Refresh tokens are NOT JWTs — they are
 * opaque, DB-stored, and handled by {@link RefreshTokenService}.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties props;

    private Key signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(props.getSecret()));
    }

    /** Build a signed access token carrying the user id (subject), email and role. */
    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + props.getAccessTokenTtlMs());
        String roleCode = user.getRole() != null ? user.getRole().getRoleCode() : null;

        return Jwts.builder()
                .setIssuer(props.getIssuer())
                .setSubject(user.getUserId().toString())
                .claim("email", user.getEmail())
                .claim("role", roleCode)
                .claim("type", "access")
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(signingKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /** Parse and verify a token, returning its claims. Throws {@link io.jsonwebtoken.JwtException} if invalid/expired. */
    public Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey())
                .requireIssuer(props.getIssuer())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public long getAccessTokenTtlMs() {
        return props.getAccessTokenTtlMs();
    }

    /** Build a signed verification token carrying the email as subject. Expiry: 24h */
    public String generateVerificationToken(String email) {
        Date now = new Date();
        // 24 hours
        Date expiry = new Date(now.getTime() + 86400000L);

        return Jwts.builder()
                .setIssuer(props.getIssuer())
                .setSubject(email)
                .claim("type", "email_verification")
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(signingKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /** Parse and verify an email verification token, returning the email. Throws exception if invalid/expired. */
    public String verifyEmailVerificationToken(String token) {
        Claims claims = parseClaims(token);
        if (!"email_verification".equals(claims.get("type"))) {
            throw new io.jsonwebtoken.JwtException("Invalid token type");
        }
        return claims.getSubject();
    }
}
