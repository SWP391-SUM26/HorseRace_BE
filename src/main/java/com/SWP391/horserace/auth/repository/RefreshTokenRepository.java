package com.SWP391.horserace.auth.repository;

import com.SWP391.horserace.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Revoke every active token for a user — used on reuse-detection and "logout everywhere". */
    @Modifying
    @Query("update RefreshToken t set t.revoked = true, t.revokedAt = :now "
            + "where t.user.userId = :userId and t.revoked = false")
    int revokeAllActiveForUser(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);
}
