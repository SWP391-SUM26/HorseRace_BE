package com.SWP391.horserace.auth.entity;

import com.SWP391.horserace.users.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Maps the {@code refresh_token} table. Stores only a SHA-256 HASH of the opaque refresh
 * token (never the raw value), so a database leak cannot be replayed as a valid token.
 * Rotated on every use; {@link #replacedByTokenId} links the rotation chain.
 */
@Entity
@Table(name = "refresh_token")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "token_id", updatable = false, nullable = false)
    private UUID tokenId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked", nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "replaced_by_token_id")
    private UUID replacedByTokenId;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @Transient
    public boolean isActive() {
        return !revoked && expiresAt.isAfter(OffsetDateTime.now());
    }
}
