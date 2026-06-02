package com.SWP391.horserace.auth.repository;

import com.SWP391.horserace.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    /** Find the most recent unused reset token for a user. */
    Optional<PasswordResetToken> findFirstByUser_UserIdAndUsedFalseOrderByCreatedAtDesc(UUID userId);

    /** Find all unused reset tokens for a user — for invalidation on resend. */
    List<PasswordResetToken> findAllByUser_UserIdAndUsedFalse(UUID userId);

    /** Count tokens created after a given time — for cooldown enforcement. */
    long countByUser_UserIdAndCreatedAtAfter(UUID userId, OffsetDateTime since);
}
