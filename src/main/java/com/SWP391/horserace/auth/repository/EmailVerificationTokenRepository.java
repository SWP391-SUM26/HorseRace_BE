package com.SWP391.horserace.auth.repository;

import com.SWP391.horserace.auth.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    /** Find the most recent unused verification token for a user. */
    Optional<EmailVerificationToken> findFirstByUser_UserIdAndUsedFalseOrderByCreatedAtDesc(UUID userId);
}
